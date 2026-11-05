package com.foldfinance.app.ui.ai

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.foldfinance.app.ui.transaction.TransactionDraft
import com.foldfinance.core.model.Transaction

/**
 * AI additions of screens shared by several hosts (the add form, the operation detail). The shell
 * provides them from Hilt view models; the defaults are empty, so previews and screenshots without
 * Hilt show the screens as they look without the model. Every hook hides itself unless the model
 * is ready (see the components in `ui/ai`).
 *
 * @property voiceInput microphone of the add form; gives a draft to prefill the form with.
 * @property receiptButton «Чек» of an expense in the detail.
 * @property renameMerchant user's display name of a merchant (`null` name — back to the original);
 *   not an AI feature itself, but it edits the same `merchant_alias` names the model fills.
 */
@Immutable
class AiHooks(
    val voiceInput: (@Composable (onDraft: (TransactionDraft) -> Unit) -> Unit)? = null,
    val receiptButton: (@Composable (Transaction) -> Unit)? = null,
    val renameMerchant: ((merchant: String, displayName: String?) -> Unit)? = null,
)

val LocalAiHooks = staticCompositionLocalOf { AiHooks() }
