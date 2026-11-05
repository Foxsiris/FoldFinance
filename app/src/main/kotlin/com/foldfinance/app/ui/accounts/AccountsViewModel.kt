package com.foldfinance.app.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.ui.transaction.UiClock
import com.foldfinance.core.data.repository.BalanceRepository
import com.foldfinance.core.data.repository.CardRepository
import com.foldfinance.core.data.repository.CategoryRepository
import com.foldfinance.core.data.repository.TransactionFilter
import com.foldfinance.core.data.repository.TransactionRepository
import com.foldfinance.core.domain.accounts.AccountsCalculator
import com.foldfinance.core.domain.accounts.CardTotals
import com.foldfinance.core.domain.accounts.InternalMovement
import com.foldfinance.core.domain.accounts.TransferFlow
import com.foldfinance.core.domain.accounts.TransferPair
import com.foldfinance.core.domain.analytics.Period
import com.foldfinance.core.model.Card
import com.foldfinance.core.model.Category
import com.foldfinance.core.model.KnownBalance
import com.foldfinance.core.model.Transaction
import com.foldfinance.parser.Direction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * A card or account in the list: what the user called it, whether it is theirs, the last balance
 * the bank reported and the month by known operations — the two are never mixed.
 *
 * @property latestBalance last «Баланс/Остаток» of the bank for the card; `null` — never reported.
 * @property totals the selected month by known operations; `null` — no operations in the month.
 */
data class AccountSummary(
    val card: Card,
    val latestBalance: KnownBalance? = null,
    val totals: CardTotals? = null,
) {
    val mask: String get() = card.mask
}

/**
 * Detail of one card.
 *
 * @property balanceHistory every balance the bank reported for the card, newest first.
 * @property operations the card's operations of the month without transfers between own cards,
 *   newest first.
 * @property movements transfers between own cards of the month that involve the card, newest first.
 */
data class AccountDetail(
    val summary: AccountSummary,
    val balanceHistory: List<KnownBalance> = emptyList(),
    val operations: List<Transaction> = emptyList(),
    val movements: List<InternalMovement> = emptyList(),
)

/**
 * State of «Счета и карты».
 *
 * @property selectedMask the card the user opened (saved): on the outer screen its detail is shown
 *   over the list, on two panes it is highlighted.
 * @property detail detail of [selectedMask] or, when nothing is selected, of the first card — what
 *   the second pane shows.
 * @property pairs transfers between own cards in the month, by «from → to».
 * @property unresolvedTransfers own transfers of the month whose direction is unknown.
 * @property renaming the card whose rename dialog is open (saved, survives folding).
 */
data class AccountsUiState(
    val loading: Boolean = true,
    val month: YearMonth,
    val today: LocalDate,
    val zone: ZoneId,
    val accounts: List<AccountSummary> = emptyList(),
    val pairs: List<TransferPair> = emptyList(),
    val unresolvedTransfers: List<InternalMovement> = emptyList(),
    val selectedMask: String? = null,
    val detail: AccountDetail? = null,
    val categories: Map<Long, Category> = emptyMap(),
    val renaming: String? = null,
) {
    /** Months after the current one have no operations: «вперёд» stops at today's month. */
    val canGoNext: Boolean get() = month < YearMonth.from(today)

    val ownCount: Int get() = accounts.count { it.card.isOwn }

    val isEmpty: Boolean get() = !loading && accounts.isEmpty()
}

/**
 * «Счета и карты»: cards from SMS and push, their known bank balances, the month by known
 * operations and the money moved between own cards. Marking a card as own and renaming it go to
 * [CardRepository].
 *
 * The selected card, the month and the open rename dialog live in [SavedStateHandle]: they survive
 * folding, rotation and activity recreation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    private val cards: CardRepository,
    private val balances: BalanceRepository,
    transactions: TransactionRepository,
    categories: CategoryRepository,
    @UiClock private val clock: Clock,
) : ViewModel() {

    private val zone: ZoneId get() = clock.zone
    private fun today(): LocalDate = LocalDate.now(clock)

    private val month: Flow<YearMonth> =
        handle.getStateFlow(KEY_MONTH, YearMonth.from(today()).toString()).map(YearMonth::parse)

    private val selected = handle.getStateFlow<String?>(KEY_SELECTED, null)
    private val renaming = handle.getStateFlow<String?>(KEY_RENAME, null)

    private val monthData: Flow<Pair<Period, List<Transaction>>> = month.flatMapLatest { m ->
        val period = Period.month(m, zone)
        transactions.observe(TransactionFilter(from = period.start, to = period.endExclusive)).map { period to it }
    }

    private val base: Flow<Base> = combine(
        cards.observeAll(),
        balances.observeLatest(),
        monthData,
        categories.observeAll(),
    ) { cardList, latest, (period, txs), cats -> buildBase(cardList, latest, period, txs, cats) }

    /** Card whose detail the second pane shows: the selected one, else the first. */
    private val detailMask: Flow<String?> = combine(base, selected) { b, sel -> sel ?: b.accounts.firstOrNull()?.mask }
        .distinctUntilChanged()

    private val history: Flow<Pair<String?, List<KnownBalance>>> = detailMask.flatMapLatest { mask ->
        if (mask == null) flowOf(null to emptyList()) else balances.observeHistory(mask).map { mask to it }
    }

    val state: StateFlow<AccountsUiState> = combine(base, selected, history, renaming) { b, sel, (historyMask, list), rename ->
        val mask = sel ?: b.accounts.firstOrNull()?.mask
        AccountsUiState(
            loading = false,
            month = YearMonth.from(b.period.startDate),
            today = today(),
            zone = zone,
            accounts = b.accounts,
            pairs = b.pairs,
            unresolvedTransfers = b.movements.filter { it.flow == TransferFlow.UNKNOWN },
            selectedMask = sel,
            detail = mask?.let { m -> detailOf(m, b, if (historyMask == m) list else emptyList()) },
            categories = b.categories,
            renaming = rename,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AccountsUiState(month = YearMonth.from(today()), today = today(), zone = zone),
    )

    /** Open a card (`null` — back to the list). */
    fun select(mask: String?) {
        handle[KEY_SELECTED] = mask
    }

    fun showPreviousMonth() = shiftMonth(-1)

    fun showNextMonth() = shiftMonth(1)

    private fun shiftMonth(by: Long) {
        val current = YearMonth.parse(handle[KEY_MONTH] ?: YearMonth.from(today()).toString())
        val target = current.plusMonths(by)
        if (target <= YearMonth.from(today())) handle[KEY_MONTH] = target.toString()
    }

    /** Mark the card as own (its transfers to other own cards stop counting as expenses) or not. */
    fun setOwn(mask: String, own: Boolean) {
        viewModelScope.launch { cards.setOwn(mask, own) }
    }

    fun startRename(mask: String) {
        handle[KEY_RENAME] = mask
    }

    fun cancelRename() {
        handle[KEY_RENAME] = null
    }

    /** Save the label of the card; an empty label removes it (the mask is shown again). */
    fun rename(mask: String, label: String) {
        handle[KEY_RENAME] = null
        val clean = label.trim().ifEmpty { null }
        viewModelScope.launch { cards.setLabel(mask, clean) }
    }

    private class Base(
        val period: Period,
        val accounts: List<AccountSummary>,
        val transactions: List<Transaction>,
        val movements: List<InternalMovement>,
        val pairs: List<TransferPair>,
        val categories: Map<Long, Category>,
    )

    private fun buildBase(
        cardList: List<Card>,
        latest: List<KnownBalance>,
        period: Period,
        txs: List<Transaction>,
        cats: List<Category>,
    ): Base {
        val totals = AccountsCalculator.cardTotals(txs, period)
        val latestByMask = latest.associateBy { it.cardMask }
        // Cards of the table, plus masks seen only in balances or operations (not registered yet).
        val known = cardList.associateBy { it.mask }
        val masks = LinkedHashSet<String>().apply {
            addAll(known.keys)
            addAll(latestByMask.keys)
            addAll(totals.keys)
        }
        val lastOperation = txs.filter { it.cardMask != null }.groupBy { it.cardMask!! }.mapValues { (_, l) -> l.maxOf { it.occurredAt } }
        // Own cards first; among them the most recently active (bank message or operation) on top.
        val accounts = masks
            .map { mask -> AccountSummary(known[mask] ?: Card(mask, label = null, isOwn = false), latestByMask[mask], totals[mask]) }
            .sortedWith(
                compareByDescending<AccountSummary> { it.card.isOwn }
                    .thenByDescending { a -> listOfNotNull(a.latestBalance?.asOf, lastOperation[a.mask]).maxOrNull() ?: Instant.MIN }
                    .thenBy { it.mask },
            )
        val movements = AccountsCalculator.internalMovements(txs, period)
        return Base(
            period = period,
            accounts = accounts,
            transactions = txs,
            movements = movements,
            pairs = AccountsCalculator.transferPairs(movements),
            categories = cats.associateBy { it.id },
        )
    }

    private fun detailOf(mask: String, b: Base, history: List<KnownBalance>): AccountDetail {
        val summary = b.accounts.firstOrNull { it.mask == mask } ?: AccountSummary(Card(mask, label = null, isOwn = false))
        return AccountDetail(
            summary = summary,
            balanceHistory = history,
            operations = b.transactions
                .filter { it.cardMask == mask && it.direction != Direction.INTERNAL_TRANSFER }
                .sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.id }),
            movements = b.movements.filter { it.involves(mask) },
        )
    }

    companion object {
        const val KEY_SELECTED = "accounts.selected"
        const val KEY_MONTH = "accounts.month"
        const val KEY_RENAME = "accounts.rename"
    }
}
