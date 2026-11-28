package de.jeisfeld.songarchive.ui

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.jeisfeld.songarchive.R
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChordSheetTest {
    @Test
    fun phonesUseSmallerMarginsThanTablets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun verifyMargins(smallestWidth: Int, side: Float, bottom: Float) {
            val config = Configuration(context.resources.configuration).apply { smallestScreenWidthDp = smallestWidth }
            val resources = context.createConfigurationContext(config).resources
            val density = resources.displayMetrics.density

            assertEquals(side, resources.getDimension(R.dimen.chord_sheet_side_padding) / density, 0.01f)
            assertEquals(bottom, resources.getDimension(R.dimen.chord_sheet_bottom_padding) / density, 0.01f)
        }

        verifyMargins(390, side = 4f, bottom = 4f)
        verifyMargins(600, side = 16f, bottom = 12f)
    }

    @Test
    fun validMarkupIsEnabledByDefaultAndCanBeDisabled() {
        val sheet = JSONObject(
            """{"version":1,"title":"Morning River","details":[],"lines":[{"kind":"chords","runs":[{"text":"Am"}]}]}"""
        )

        assertNotNull(ChordSheet.parse(sheet.toString()))
        assertNull(ChordSheet.parse(sheet.put("enabled", false).toString()))
        assertNotNull(ChordSheet.parse(sheet.put("enabled", true).toString()))
    }

    @Test
    fun invalidMarkupIsUnavailableEvenWhenEnabled() {
        assertNull(ChordSheet.parse("""{"version":1,"enabled":true,"title":"Missing chords","details":[],"lines":[]}"""))
        assertNull(ChordSheet.parse("""{"version":1,"enabled":true,"title":"Invalid chords","details":[],"lines":[{"kind":"chords","runs":[{"text":null}]}]}"""))
    }
}
