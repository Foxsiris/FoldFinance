package com.foldfinance.app.ui.ai.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foldfinance.app.R
import com.foldfinance.app.ui.ai.AiProgress
import com.foldfinance.app.ai.LocalAi
import com.foldfinance.app.ai.features.common.AiAvailability
import com.foldfinance.app.ai.features.common.toAvailability
import com.foldfinance.app.ai.features.voice.OnDeviceSpeech
import com.foldfinance.app.ai.features.voice.VoiceDraft
import com.foldfinance.app.ai.features.voice.VoiceParser
import com.foldfinance.app.prefs.AppPreferences
import com.foldfinance.app.ui.format.MoneyFormat
import com.foldfinance.app.ui.theme.FoldShapes
import com.foldfinance.app.ui.transaction.AmountEntry
import com.foldfinance.app.ui.transaction.TransactionDraft
import com.foldfinance.core.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Phase of a voice input. */
sealed interface VoicePhase {
    data object Idle : VoicePhase
    data class Listening(val partial: String) : VoicePhase
    data class Parsing(val transcript: String) : VoicePhase

    /** Nothing heard / recogniser error; [languageMissing] — the Russian on-device pack is not installed. */
    data class Error(val languageMissing: Boolean) : VoicePhase
}

/** Voice cash input (п.6): speech → [VoiceParser] → [VoiceDraft]. */
@HiltViewModel
class VoiceInputViewModel @Inject constructor(
    ai: LocalAi,
    private val parser: VoiceParser,
    private val categories: CategoryRepository,
    private val preferences: AppPreferences,
) : ViewModel() {
    val availability: StateFlow<AiAvailability> =
        ai.status.map { it.toAvailability() }.stateIn(viewModelScope, SharingStarted.Eagerly, ai.status.value.toAvailability())

    private val _phase = MutableStateFlow<VoicePhase>(VoicePhase.Idle)
    val phase: StateFlow<VoicePhase> = _phase.asStateFlow()

    /** The Russian model is missing: the button hides for this session. */
    private val _languageMissing = MutableStateFlow(false)
    val languageMissing: StateFlow<Boolean> = _languageMissing.asStateFlow()

    fun onSpeech(event: OnDeviceSpeech.Event, onDraft: (VoiceDraft) -> Unit) {
        when (event) {
            OnDeviceSpeech.Event.Listening -> _phase.value = VoicePhase.Listening("")
            is OnDeviceSpeech.Event.Partial -> _phase.value = VoicePhase.Listening(event.text)
            is OnDeviceSpeech.Event.Error -> {
                if (event.languageMissing) _languageMissing.value = true
                _phase.value = VoicePhase.Error(event.languageMissing)
            }
            is OnDeviceSpeech.Event.Final -> {
                _phase.value = VoicePhase.Parsing(event.text)
                viewModelScope.launch {
                    val draft = parser.parse(event.text, categories.getAll(), preferences.defaultCurrency.first())
                    _phase.value = VoicePhase.Idle
                    onDraft(draft)
                }
            }
        }
    }

    fun reset() {
        _phase.value = VoicePhase.Idle
    }
}

/**
 * Microphone button for the add form: «кофе двести пятьдесят наличными» → [onDraft] with a cash
 * draft for the user to confirm. Hidden unless the model is ready and the device has an
 * on-device recogniser (audio never leaves the phone). Asks for RECORD_AUDIO after a «зачем» dialog.
 */
@Composable
fun VoiceAddButton(
    onDraft: (VoiceDraft) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VoiceInputViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val availability by viewModel.availability.collectAsStateWithLifecycle()
    val languageMissing by viewModel.languageMissing.collectAsStateWithLifecycle()
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val deviceSupports = remember { OnDeviceSpeech.isAvailable(context) }
    var showRationale by rememberSaveable { mutableStateOf(false) }
    val speech = remember { OnDeviceSpeech(context) }
    DisposableEffect(speech) {
        onDispose {
            speech.destroy()
            viewModel.reset()
        }
    }
    val start = { speech.start { event -> viewModel.onSpeech(event, onDraft) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start()
    }

    if (availability != AiAvailability.Ready || !deviceSupports) return
    // Without the Russian on-device model the button goes; the error explaining why stays open.
    if (!languageMissing) {
        FilledTonalIconButton(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (granted) start() else showRationale = true
            },
            modifier = modifier.size(56.dp),
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = stringResource(R.string.aif_voice_button))
        }
    }

    if (showRationale) {
        MicRationaleDialog(
            onAllow = {
                showRationale = false
                permission.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = { showRationale = false },
        )
    }
    if (phase != VoicePhase.Idle) {
        Dialog(onDismissRequest = {
            speech.stop()
            viewModel.reset()
        }) {
            VoiceListeningContent(phase, onStop = { speech.stop() }, onClose = {
                speech.destroy()
                viewModel.reset()
            })
        }
    }
}

/** «Зачем микрофон» — shown before the system permission request (CLAUDE.md). */
@Composable
fun MicRationaleDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Mic, contentDescription = null) },
        title = { Text(stringResource(R.string.aif_mic_why_title)) },
        text = { Text(stringResource(R.string.aif_mic_why_text)) },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.aif_mic_allow)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.aif_not_now)) } },
    )
}

/** The listening panel: what was heard so far, then «Разбираю…». Stateless for screenshots. */
@Composable
fun VoiceListeningContent(phase: VoicePhase, onStop: () -> Unit = {}, onClose: () -> Unit = {}, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.widthIn(max = 420.dp), shape = FoldShapes.summary, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(
                        when (phase) {
                            is VoicePhase.Parsing -> R.string.aif_voice_parsing
                            is VoicePhase.Error -> R.string.aif_voice_error
                            else -> R.string.aif_voice_listening
                        },
                    ),
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val heard = when (phase) {
                is VoicePhase.Listening -> phase.partial
                is VoicePhase.Parsing -> phase.transcript
                else -> ""
            }
            Text(
                when {
                    phase is VoicePhase.Error && phase.languageMissing -> stringResource(R.string.aif_voice_language_missing)
                    phase is VoicePhase.Error -> stringResource(R.string.aif_voice_not_heard)
                    heard.isEmpty() -> stringResource(R.string.aif_voice_example)
                    else -> "«$heard»"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (heard.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (phase is VoicePhase.Parsing || phase is VoicePhase.Listening) AiProgress(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (phase is VoicePhase.Listening) TextButton(onClick = onStop) { Text(stringResource(R.string.aif_voice_done)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.aif_close)) }
            }
        }
    }
}

/**
 * Draft of the add form from a voice draft. Amount through [AmountEntry] (exact text, never a
 * Double); [cashNote] — «Наличные» from resources, so the operation says how it was paid.
 */
fun VoiceDraft.toTransactionDraft(cashNote: String): TransactionDraft = TransactionDraft(
    amountText = amountMinor?.let { AmountEntry.fromMinor(it, MoneyFormat.fractionDigits(currency)) }.orEmpty(),
    currency = currency,
    direction = direction.name,
    categoryId = categoryId,
    merchant = merchant.orEmpty(),
    note = if (isCash) cashNote else "",
)
