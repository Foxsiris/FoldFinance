package com.foldfinance.app.ui.onboarding

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.foldfinance.app.ingest.InMemoryPreferencesStore
import com.foldfinance.app.ingest.IngestPermissions
import com.foldfinance.app.ingest.IngestRuntimePrefs
import com.foldfinance.app.ingest.IngestScheduler
import com.foldfinance.app.ingest.push.InstalledApp
import com.foldfinance.core.data.settings.IngestSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OnboardingViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = IngestRuntimePrefs(InMemoryPreferencesStore())
    private val settings = IngestSettings(InMemoryPreferencesStore())
    private val apps = listOf(
        InstalledApp("ru.sberbankmobile", "СберБанк", isKnownBank = true),
        InstalledApp("org.telegram.messenger", "Telegram", isKnownBank = false),
    )

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = OnboardingViewModel(
        handle = handle,
        prefs = prefs,
        settings = settings,
        permissions = IngestPermissions(context),
        scheduler = IngestScheduler(context),
        appsSource = { apps },
    )

    private fun grantSms() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
    }

    @Test fun `starts with the sms explanation`() {
        val vm = viewModel()
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.SmsIntro)
        assertThat(vm.state.value.stepNumber).isEqualTo(1)
        assertThat(vm.state.value.stepCount).isEqualTo(OnboardingStep.flow(IngestPermissions.needsPostNotificationsRequest).size)
    }

    @Test fun `denied sms leads to restricted settings, granted on return moves on`() {
        val vm = viewModel()
        vm.onSmsPermissionResult(granted = false)
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.SmsRestricted)
        assertThat(vm.state.value.stepNumber).isEqualTo(1) // the detour is not a separate step

        grantSms()
        vm.refresh() // ON_RESUME after «Разрешить ограниченные настройки»
        assertThat(vm.state.value.step).isEqualTo(OnboardingStep.NotificationAccess)
    }

    @Test fun `current step survives recreation through SavedStateHandle`() {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.next()
        vm.next()
        val restored = viewModel(SavedStateHandle(mapOf(OnboardingViewModel.KEY_STEP to handle.get<String>(OnboardingViewModel.KEY_STEP))))
        assertThat(restored.state.value.step).isEqualTo(OnboardingStep.NotificationSources)
    }

    @Test fun `sources step preselects installed known banks and saves the whitelist`() {
        val vm = viewModel(SavedStateHandle(mapOf(OnboardingViewModel.KEY_STEP to OnboardingStep.NotificationSources.name)))
        assertThat(vm.state.value.sources.apps).isEqualTo(apps)
        assertThat(vm.state.value.sources.selected).containsExactly("ru.sberbankmobile")

        vm.toggleSource("org.telegram.messenger")
        vm.toggleSource("ru.sberbankmobile")
        vm.saveSources()

        assertThat(runBlocking { settings.pushPackageWhitelist() }).containsExactly("org.telegram.messenger")
        assertThat(vm.state.value.step).isNotEqualTo(OnboardingStep.NotificationSources)
    }

    @Test fun `every step can be skipped up to the end and finishing stores the flag`() {
        val vm = viewModel()
        repeat(OnboardingStep.entries.size + 1) { if (!vm.finished.value) vm.next() }
        assertThat(vm.finished.value).isTrue()
        assertThat(runBlocking { prefs.onboardingCompleted.first() }).isTrue()
    }

    @Test fun `completed onboarding is reported right away`() {
        runBlocking { prefs.setOnboardingCompleted(true) }
        assertThat(viewModel().alreadyCompleted.value).isTrue()
    }
}
