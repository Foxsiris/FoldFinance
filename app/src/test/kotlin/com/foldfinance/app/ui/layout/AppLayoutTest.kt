package com.foldfinance.app.ui.layout

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLayoutTest {

    private fun verticalHinge(x: Float, height: Float, state: FoldState = FoldState.FLAT, separating: Boolean = false, width: Float = 0f) =
        Fold(DpBounds(x - width / 2, 0f, x + width / 2, height), state, FoldOrientation.VERTICAL, separating)

    private fun horizontalHinge(y: Float, width: Float, state: FoldState = FoldState.HALF_OPENED, separating: Boolean = true) =
        Fold(DpBounds(0f, y, width, y), state, FoldOrientation.HORIZONTAL, separating)

    @Test fun `outer screen is compact`() {
        assertThat(computeAppLayout(411f, 658f, emptyList())).isEqualTo(AppLayout.Compact(411f, 658f))
    }

    @Test fun `inner screen flat - split on the non-separating hinge`() {
        val layout = computeAppLayout(882f, 662f, listOf(verticalHinge(441f, 662f)))
        assertThat(layout).isEqualTo(AppLayout.Book(882f, 662f, 441f, 0f, SplitSource.Hinge))
    }

    @Test fun `inner screen half-opened in book posture - split on the hinge`() {
        val layout = computeAppLayout(882f, 662f, listOf(verticalHinge(441f, 662f, FoldState.HALF_OPENED, separating = true)))
        assertThat(layout).isEqualTo(AppLayout.Book(882f, 662f, 441f, 0f, SplitSource.Hinge))
    }

    @Test fun `hinge with physical width is reported for the keep-out zone`() {
        val layout = computeAppLayout(900f, 700f, listOf(verticalHinge(450f, 700f, separating = true, width = 20f)))
        assertThat(layout).isEqualTo(AppLayout.Book(900f, 700f, 450f, 20f, SplitSource.Hinge))
    }

    @Test fun `off-centre hinge is followed exactly`() {
        val layout = computeAppLayout(900f, 700f, listOf(verticalHinge(500f, 700f)))
        assertThat((layout as AppLayout.Book).splitAtDp).isEqualTo(500f)
    }

    @Test fun `tabletop - horizontal half-opened hinge splits top and bottom`() {
        val layout = computeAppLayout(662f, 882f, listOf(horizontalHinge(441f, 662f)))
        assertThat(layout).isEqualTo(AppLayout.Tabletop(662f, 882f, 441f, 0f))
    }

    @Test fun `tabletop wins over width`() {
        val layout = computeAppLayout(882f, 900f, listOf(horizontalHinge(450f, 882f)))
        assertThat(layout).isInstanceOf(AppLayout.Tabletop::class.java)
    }

    @Test fun `flat horizontal hinge on a portrait inner screen is ignored`() {
        val layout = computeAppLayout(662f, 882f, listOf(horizontalHinge(441f, 662f, FoldState.FLAT, separating = false)))
        assertThat(layout).isEqualTo(AppLayout.Book(662f, 882f, 331f, 0f, SplitSource.Proportion))
    }

    @Test fun `medium width without hinge already gets two panes`() {
        val layout = computeAppLayout(600f, 800f, emptyList())
        assertThat(layout).isEqualTo(AppLayout.Book(600f, 800f, 300f, 0f, SplitSource.Proportion))
    }

    @Test fun `just below medium is compact`() {
        assertThat(computeAppLayout(599f, 800f, emptyList())).isInstanceOf(AppLayout.Compact::class.java)
    }

    @Test fun `split-screen half of the inner screen - narrow window, no hinge`() {
        assertThat(computeAppLayout(441f, 662f, emptyList())).isEqualTo(AppLayout.Compact(441f, 662f))
    }

    @Test fun `split-screen half - hinge reported on the window edge is ignored`() {
        val layout = computeAppLayout(441f, 662f, listOf(verticalHinge(441f, 662f)))
        assertThat(layout).isEqualTo(AppLayout.Compact(441f, 662f))
    }

    @Test fun `wide split-screen window with the hinge at its edge splits by proportion`() {
        val layout = computeAppLayout(700f, 662f, listOf(verticalHinge(0f, 662f)))
        assertThat(layout).isEqualTo(AppLayout.Book(700f, 662f, 350f, 0f, SplitSource.Proportion))
    }

    @Test fun `small pop-up window is compact`() {
        assertThat(computeAppLayout(360f, 520f, emptyList())).isEqualTo(AppLayout.Compact(360f, 520f))
    }

    @Test fun `large pop-up window gets two panes by proportion`() {
        val layout = computeAppLayout(720f, 540f, emptyList())
        assertThat(layout).isEqualTo(AppLayout.Book(720f, 540f, 360f, 0f, SplitSource.Proportion))
    }

    @Test fun `pop-up window crossing the hinge keeps panes on the hinge`() {
        // Window moved so that the hinge is 300 dp from its left edge.
        val layout = computeAppLayout(700f, 500f, listOf(verticalHinge(300f, 500f)))
        assertThat(layout).isEqualTo(AppLayout.Book(700f, 500f, 300f, 0f, SplitSource.Hinge))
    }

    @Test fun `narrow window split by a separating hinge still becomes two panes`() {
        val layout = computeAppLayout(560f, 700f, listOf(verticalHinge(280f, 700f, separating = true)))
        assertThat(layout).isEqualTo(AppLayout.Book(560f, 700f, 280f, 0f, SplitSource.Hinge))
    }

    @Test fun `tabletop needs room for both halves`() {
        val layout = computeAppLayout(411f, 658f, listOf(horizontalHinge(100f, 411f)))
        assertThat(layout).isInstanceOf(AppLayout.Compact::class.java)
    }

    @Test fun `phone landscape without hinge is two panes`() {
        assertThat(computeAppLayout(658f, 411f, emptyList())).isInstanceOf(AppLayout.Book::class.java)
    }

    @Test fun `readings convert px to dp before computing`() {
        val readings = WindowReadings(
            widthPx = 2316,
            heightPx = 1738,
            density = 2.625f,
            folds = listOf(
                FoldReading(PxBounds(1158, 0, 1158, 1738), FoldState.FLAT, FoldOrientation.VERTICAL, false, FoldOcclusion.NONE),
            ),
        )
        val layout = readings.toAppLayout() as AppLayout.Book
        assertThat(layout.splitAtDp).isWithin(0.01f).of(441.14f)
        assertThat(layout.splitSource).isEqualTo(SplitSource.Hinge)
        assertThat(readings.sizeClass.widthLabel()).isEqualTo("Expanded")
        assertThat(readings.sizeClass.heightLabel()).isEqualTo("Medium")
    }

    @Test fun `size class labels`() {
        assertThat(WindowReadings.fromDp(411f, 658f, 2f, emptyList()).sizeClass.widthLabel()).isEqualTo("Compact")
        assertThat(WindowReadings.fromDp(662f, 882f, 2f, emptyList()).sizeClass.widthLabel()).isEqualTo("Medium")
        assertThat(WindowReadings.fromDp(411f, 658f, 2f, emptyList()).sizeClass.heightLabel()).isEqualTo("Medium")
        assertThat(WindowReadings.fromDp(658f, 411f, 2f, emptyList()).sizeClass.heightLabel()).isEqualTo("Compact")
    }

    @Test fun `pane split keeps the hinge clear`() {
        // 882 dp window, 80 dp rail: container 802 dp, hinge 441 → 361 inside the container. 1 px = 1 dp here.
        val split = PaneSplit.compute(containerSize = 802, splitInContainer = 361f, hingeSize = 0f, clearance = 12f, minPane = 160f)
        assertThat(split).isEqualTo(PaneSplit(firstSize = 349, gapStart = 349, gapEnd = 373, secondSize = 429))
        // Centre of the gap is the hinge.
        assertThat((split.gapStart + split.gapEnd) / 2).isEqualTo(361)
    }

    @Test fun `pane split includes physical hinge width`() {
        val split = PaneSplit.compute(containerSize = 900, splitInContainer = 450f, hingeSize = 20f, clearance = 12f, minPane = 160f)
        assertThat(split.gapStart).isEqualTo(428)
        assertThat(split.gapEnd).isEqualTo(472)
    }

    @Test fun `pane split falls back to the middle when the hinge is outside the container`() {
        val split = PaneSplit.compute(containerSize = 600, splitInContainer = 50f, hingeSize = 0f, clearance = 12f, minPane = 160f)
        assertThat(split.gapStart).isEqualTo(288)
        assertThat(split.gapEnd).isEqualTo(312)
    }
}
