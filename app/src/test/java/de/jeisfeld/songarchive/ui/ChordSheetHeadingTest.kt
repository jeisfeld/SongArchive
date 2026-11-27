package de.jeisfeld.songarchive.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class ChordSheetHeadingTest {
    @Test
    fun creditsAndReferencesStaySeparateFromCapoAndMeter() {
        val details = chordSheetHeadingDetails(listOf("2/20", "(Ada Example)", "(¾)Capo 3").map { AnnotatedString(it) })

        assertEquals(listOf("2/20", "(Ada Example)"), details.credits.map { it.text })
        assertEquals(listOf("(¾)Capo 3"), details.capo)
    }

    @Test
    fun preservesAnnotationsAfterAnUnspacedOrTuningPrefixedCapo() {
        val comment = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append("[Translation]") }
        }
        val details = chordSheetHeadingDetails(listOf(AnnotatedString("Capo0 ") + comment, AnnotatedString("Drop-D, Capo 2")))

        assertEquals(listOf(comment), details.credits)
        assertEquals(listOf("Capo0", "Drop-D, Capo 2"), details.capo)
    }
}
