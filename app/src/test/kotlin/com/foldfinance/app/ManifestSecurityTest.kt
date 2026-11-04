package com.foldfinance.app

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Privacy invariants of the merged manifest (CLAUDE.md, «Безопасность и приватность»):
 * the app never gets network access and is excluded from system backup.
 *
 * Robolectric installs the app from the merged manifest of the variant under test, so a library
 * that sneaks in INTERNET would fail here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ManifestSecurityTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun requestedPermissions(): List<String> {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.toList().orEmpty()
    }

    @Test fun `merged manifest has no network permissions`() {
        val permissions = requestedPermissions()
        assertThat(permissions).isNotEmpty()
        assertThat(permissions).contains("android.permission.READ_SMS")
        assertThat(permissions).doesNotContain("android.permission.INTERNET")
        assertThat(permissions).doesNotContain("android.permission.ACCESS_NETWORK_STATE")
    }

    @Test fun `system backup is disabled`() {
        val flags = context.applicationInfo.flags
        assertThat(flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
    }
}
