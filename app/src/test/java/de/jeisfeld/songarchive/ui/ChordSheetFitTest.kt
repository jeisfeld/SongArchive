package de.jeisfeld.songarchive.ui

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordSheetFitTest {
    @Test
    fun additionalTextShrinksToTheWidthLeftAfterItsAnnotation() {
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
}
