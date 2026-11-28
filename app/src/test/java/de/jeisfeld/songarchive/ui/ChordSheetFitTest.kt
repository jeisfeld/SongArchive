package de.jeisfeld.songarchive.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordSheetFitTest {
    @Test
    fun headingShrinksToTheAvailableWidth() {
        assertEquals(1f, fitChordTextScale(520, 480), 0.001f)
        assertEquals(418f / 480, fitChordTextScale(420, 480), 0.001f)
    }

    @Test
    fun wideSheetUsesLandscapeBoundsEvenWhenWindowStaysInPortrait() {
        val fit = chordSheetFit(IntSize(400, 120), IntSize(390, 840), 32, 60)

        assertTrue(fit.landscape)
        assertTrue(fit.sideways)
        assertEquals(IntSize(840, 390), fit.viewport)
        assertEquals(806f / 400, fit.scale, 0.001f)
    }

    @Test
    fun landscapeWindowFitsToAvailableHeightWhenThatIsTheSmallerDimension() {
        val fit = chordSheetFit(IntSize(400, 200), IntSize(1000, 390), 32, 60)

        assertTrue(fit.landscape)
        assertFalse(fit.sideways)
        assertEquals(328f / 200, fit.scale, 0.001f)
    }

    @Test
    fun wideSheetWithAdditionalVersesFitsAShortLandscapeViewport() {
        val fit = chordSheetFit(IntSize(740, 447), IntSize(390, 844), 32, 60, hasNotes = true)

        assertTrue(fit.sideways)
        assertEquals(328f / 447, fit.scale, 0.001f)
    }

    @Test
    fun tallSheetStaysUprightAndKeepsEnoughTextSizeToScroll() {
        val fit = chordSheetFit(IntSize(384, 1600), IntSize(390, 840), 32, 60)

        assertFalse(fit.landscape)
        assertFalse(fit.sideways)
        assertEquals(0.8f, fit.scale, 0.001f)
    }

    @Test
    fun zoomKeepsTheSheetUnderThePinchAndPansToEachEdge() {
        val transform = ChordSheetTransform()
        val size = IntSize(400, 240)
        val centroid = Offset(100f, 60f)
        val zoomed = transform.update(size, size, centroid, Offset(15f, 10f), 2f)

        assertEquals(2f, zoomed.scale, 0.001f)
        assertEquals(Offset(-85f, -50f), zoomed.origin(size, size))
        assertEquals(Offset(-400f, -240f),
            zoomed.update(size, size, centroid, Offset(-1000f, -1000f), 1f).origin(size, size))
        assertEquals(Offset.Zero,
            zoomed.update(size, size, centroid, Offset(1000f, 1000f), 1f).origin(size, size))
    }

    @Test
    fun aTallSheetCanPanAtFitAndRecentersHorizontallyWhenZoomingBackOut() {
        val page = IntSize(300, 700)
        val viewport = IntSize(400, 500)
        val center = Offset(200f, 250f)
        val transform = ChordSheetTransform()
        val scrolled = transform.update(page, viewport, center, Offset(-200f, -100f), 1f)

        assertEquals(Offset(50f, 0f), transform.origin(page, viewport))
        assertEquals(Offset(50f, -100f), scrolled.origin(page, viewport))
        val zoomed = scrolled.update(page, viewport, center, Offset.Zero, 10f)
        assertEquals(5f, zoomed.scale, 0.001f)
        val fitted = zoomed.update(page, viewport, center, Offset.Zero, 0.2f)
        assertEquals(1f, fitted.scale, 0.001f)
        assertEquals(scrolled.origin(page, viewport), fitted.origin(page, viewport))
    }

    @Test
    fun pinchingInCanShrinkBelowTheFittedSize() {
        val page = IntSize(400, 240)
        val fitted = ChordSheetTransform()
        val smaller = fitted.update(page, page, Offset(200f, 120f), Offset.Zero, 0.2f)

        assertEquals(0.5f, smaller.scale, 0.001f)
        assertEquals(Offset(100f, 60f), smaller.origin(page, page))
    }

    @Test
    fun pairsUseOnlySpareHeightUpToTheMaximumGap() {
        assertEquals(6, chordPairGap(500, 200, 5, 6))
        assertEquals(3, chordPairGap(218, 200, 5, 6))
        assertEquals(0, chordPairGap(180, 200, 5, 6))
        assertEquals(0, chordPairGap(500, 200, 0, 6))
    }
}
