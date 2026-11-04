package com.foldfinance.app.ingest.sms

import com.foldfinance.parser.KnownBankSources
import com.foldfinance.parser.MessageChannel
import com.foldfinance.parser.MessageMeta
import com.foldfinance.parser.ParseResult
import com.foldfinance.parser.ParserRegistry
import javax.inject.Inject

/**
 * Decides which SMS may be stored at all. Privacy comes first: the inbox holds personal chats,
 * one-time codes and promos, and none of that belongs in the app's database.
 *
 * The message is parsed in memory first; only then is it handed to `IngestRepository.ingest`
 * (which stores a `RawMessage` for everything it receives):
 *
 * - **NotFinancial is never stored**, from any sender: one-time codes, promos, service texts and
 *   private messages stay only in the system inbox. Nothing is lost by that: SMS stay on the phone,
 *   so a full rescan after a parser upgrade re-reads them and lets the new parser decide again
 *   (see `ReparseWorker`).
 * - **Known bank senders** ([KnownBankSources]): everything else is stored — `Parsed` and any
 *   `Uncertain` (even without an amount), so the review queue and future parser versions see it.
 * - **Unknown senders** go through `GenericParser` and are stored only when it actually found money:
 *   `Parsed`, or `Uncertain` with a partial operation or a balance. An `Uncertain` without either
 *   (too long, no amount, parser error) from an unknown sender is dropped.
 * - Empty bodies and empty addresses are dropped.
 */
class SmsAdmission @Inject constructor(private val registry: ParserRegistry) {

    fun admit(address: String, body: String, receivedAtMillis: Long): Boolean {
        if (address.isBlank() || body.isBlank()) return false
        val meta = MessageMeta(channel = MessageChannel.SMS, sender = address, receivedAtMillis = receivedAtMillis)
        val knownBank = KnownBankSources.forMeta(meta) != null
        return when (val result = registry.parse(body, meta)) {
            ParseResult.NotFinancial -> false
            is ParseResult.Parsed -> true
            is ParseResult.Uncertain -> knownBank || result.partial != null || result.balance != null
        }
    }
}
