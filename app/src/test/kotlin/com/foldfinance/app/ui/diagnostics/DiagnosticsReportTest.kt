package com.foldfinance.app.ui.diagnostics

import com.foldfinance.app.ui.layout.FoldOcclusion
import com.foldfinance.app.ui.layout.FoldOrientation
import com.foldfinance.app.ui.layout.FoldReading
import com.foldfinance.app.ui.layout.FoldState
import com.foldfinance.app.ui.layout.PxBounds
import com.foldfinance.app.ui.layout.WindowReadings
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagnosticsReportTest {

    @Test fun `report lists window, size class, layout and every fold`() {
        val readings = WindowReadings(
            widthPx = 2316,
            heightPx = 1738,
            density = 2.625f,
            folds = listOf(
                FoldReading(PxBounds(1158, 0, 1158, 1738), FoldState.FLAT, FoldOrientation.VERTICAL, false, FoldOcclusion.NONE),
            ),
        )
        val report = diagnosticsReport(readings, device = "samsung SM-TEST", sdkInt = 36)
        assertThat(report).isEqualTo(
            """
            FoldFinance — диагностика окна
            Устройство: samsung SM-TEST, Android SDK 36
            Окно: 882.3 × 662.1 dp (2316 × 1738 px), density 2.625
            Size class: width Expanded, height Medium
            AppLayout: Book splitX=441.1dp (Hinge) hinge=0dp
            FoldingFeature 1: bounds px [1158, 0, 1158, 1738], dp [441.1, 0, 441.1, 662.1], state FLAT, orientation VERTICAL, isSeparating false, occlusionType NONE
            """.trimIndent(),
        )
    }

    @Test fun `report without folds says so`() {
        val report = diagnosticsReport(WindowReadings(1080, 1728, 2.625f, emptyList()), "x", 36)
        assertThat(report).contains("AppLayout: Compact")
        assertThat(report).endsWith("FoldingFeature: нет")
    }

    @Test fun `number formatting`() {
        assertThat(DiagnosticsFormat.dp(441f)).isEqualTo("441")
        assertThat(DiagnosticsFormat.dp(661.66f)).isEqualTo("661.7")
        assertThat(DiagnosticsFormat.density(2.625f)).isEqualTo("2.625")
        assertThat(DiagnosticsFormat.density(3f)).isEqualTo("3")
    }
}
