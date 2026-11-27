package de.jeisfeld.songarchive.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.roundToInt

private val chordLineKinds = setOf("chords", "lyrics", "note", "pitch")

internal data class ChordSheet(
    val title: AnnotatedString,
    val headingDetails: ChordSheetHeadingDetails,
    val lines: List<ChordSheetLine>
) {
    companion object {
        fun parse(markup: String?): ChordSheet? {
            if (markup.isNullOrBlank()) return null
            return try {
                val sheet = JSONObject(markup)
                if (sheet.getInt("version") != 1) return null
                val details = sheet.getJSONArray("details")
                val rows = sheet.getJSONArray("lines")
                val lines = List(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    val kind = row.getString("kind")
                    if (kind !in chordLineKinds) return null
                    val runs = row.getJSONArray("runs")
                    val text = buildAnnotatedString {
                        repeat(runs.length()) { runIndex ->
                            val run = runs.getJSONObject(runIndex)
                            withStyle(
                                SpanStyle(
                                    color = chordSheetColor(run.optString("tone"), Color.Unspecified),
                                    fontWeight = FontWeight.Bold.takeIf { run.optBoolean("bold") },
                                    fontStyle = FontStyle.Italic.takeIf { run.optBoolean("italic") },
                                    textDecoration = TextDecoration.Underline.takeIf { run.optString("underline").isNotEmpty() },
                                    baselineShift = BaselineShift.Superscript.takeIf { run.optBoolean("superscript") }
                                )
                            ) {
                                append(run.getString("text"))
                            }
                        }
                    }
                    ChordSheetLine(
                        kind, text, row.optString("aside"), row.optString("asideTone"),
                        row.opt("paragraphStart") as? Boolean
                    )
                }
                if (lines.none { it.kind == "chords" && (it.text.isNotBlank() || it.aside.isNotBlank()) }) return null
                val headingRuns = sheet.optJSONArray("headingRuns")
                ChordSheet(
                    readHeadingText(sheet.getString("title"), headingRuns?.optJSONArray(0)),
                    chordSheetHeadingDetails(List(details.length()) {
                        readHeadingText(details.getString(it), headingRuns?.optJSONArray(it + 1))
                    }),
                    lines
                )
            } catch (_: JSONException) {
                null
            }
        }
    }
}

private fun readHeadingText(text: String, runs: JSONArray?): AnnotatedString {
    if (runs == null) return AnnotatedString(text)
    val styled = buildAnnotatedString {
        repeat(runs.length()) { index ->
            val run = runs.optJSONObject(index)
            val part = run?.optString("text").orEmpty()
            if (run?.optString("tone") == "comment") {
                withStyle(SpanStyle(color = chordSheetColor("comment"), fontWeight = FontWeight.Normal)) {
                    append(part)
                }
            } else {
                append(part)
            }
        }
    }
    return styled.takeIf { it.text == text } ?: AnnotatedString(text)
}

internal data class ChordSheetHeadingDetails(val credits: List<AnnotatedString>, val capo: List<String>)

private val capoDetail = Regex(
    """^((?:drop-d,\s*|\(?[\d¾:/]+\)?\s*)?capo\s*\d+)\b\s*(.*)$""",
    RegexOption.IGNORE_CASE
)

internal fun chordSheetHeadingDetails(details: List<AnnotatedString>): ChordSheetHeadingDetails {
    val credits = mutableListOf<AnnotatedString>()
    val capo = mutableListOf<String>()
    for (detail in details) {
        val match = capoDetail.matchEntire(detail.text)
        if (match == null) {
            credits.add(detail)
        } else {
            capo.add(match.groupValues[1])
            val trailingText = match.groups[2]
            if (trailingText != null && trailingText.value.isNotEmpty()) {
                credits.add(detail.subSequence(trailingText.range.first, detail.length))
            }
        }
    }
    return ChordSheetHeadingDetails(credits, capo)
}

internal data class ChordSheetLine(
    val kind: String,
    val text: AnnotatedString,
    val aside: String,
    val asideTone: String,
    val paragraphStart: Boolean?
)

private val SheetInk = Color(0xFF171717)

private fun chordSheetColor(tone: String, default: Color = SheetInk) = when (tone) {
    "chords" -> Color(0xFF00649E)
    "note" -> Color(0xFFA61B1B)
    "pitch" -> Color(0xFF806000)
    "detail" -> Color(0xFF16704B)
    "comment" -> Color(0xFF757575)
    "alternate" -> Color(0xFF62ACE6)
    else -> default
}

private val chordRowStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, lineHeight = 22.sp)
private val chordAsideStyle = TextStyle(fontSize = 12.5.sp, lineHeight = 22.sp)
private val chordNoteStyle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.6.sp, lineHeight = 18.7.sp)
private val chordTitleStyle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
private val chordDetailsStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
private val chordColumnGap = 16.dp
private val chordLyricOverlap = 3.dp
private val chordNoteParagraphGap = 11.dp

@Composable
internal fun ChordSheetContent(sheet: ChordSheet, onLandscapeSheet: (Boolean) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val naturalWidth = remember(sheet, measurer, density) {
        with(density) {
            val minWidth = (24 * chordRowStyle.fontSize.toPx()).roundToInt()
            val widestRow = sheet.lines.maxOf { line ->
                val mainWidth = if (line.kind == "note") 0 else {
                    measurer.measure(line.text, chordRowStyle, softWrap = false).size.width
                }
                val asideWidth = if (line.aside.isEmpty()) 0 else {
                    chordColumnGap.roundToPx() + measurer.measure(line.aside, chordAsideStyle, softWrap = false).size.width
                }
                mainWidth + asideWidth
            }
            maxOf(minWidth, widestRow + 1)
        }
    }
    val hasNotes = sheet.lines.any { it.kind == "note" }
    val sidePadding = 16.dp
    val topPadding = 48.dp
    val bottomPadding = 12.dp

    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val naturalPageWidth = naturalWidth.toDp()
        val natural = subcompose("natural") { ChordSheetPage(sheet, naturalPageWidth, 1f) }
            .single().measure(Constraints.fixedWidth(naturalWidth))
        val window = IntSize(constraints.maxWidth, constraints.maxHeight)
        val fit = chordSheetFit(
            sheet = IntSize(natural.width, natural.height),
            window = window,
            horizontalPadding = 2 * sidePadding.roundToPx(),
            verticalPadding = topPadding.roundToPx() + bottomPadding.roundToPx(),
            hasNotes = hasNotes
        )
        val sheetWidth = ceil(naturalWidth * fit.scale).toInt().toDp()
        val page = subcompose("fitted") {
            LaunchedEffect(fit.landscape) { onLandscapeSheet(fit.landscape) }
            Box(
                modifier = Modifier.fillMaxSize().padding(
                    start = sidePadding, end = sidePadding, top = topPadding, bottom = bottomPadding
                ),
                contentAlignment = Alignment.Center
            ) {
                ChordSheetPage(
                    sheet,
                    sheetWidth,
                    fit.scale,
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                        .width(sheetWidth)
                )
            }
        }.single().measure(Constraints.fixed(fit.viewport.width, fit.viewport.height))

        layout(window.width, window.height) {
            if (fit.sideways) {
                page.placeWithLayer((window.width - page.width) / 2, (window.height - page.height) / 2) {
                    rotationZ = 90f
                }
            } else {
                page.place(0, 0)
            }
        }
    }
}

internal data class ChordSheetFit(
    val landscape: Boolean,
    val sideways: Boolean,
    val viewport: IntSize,
    val scale: Float
)

internal fun chordSheetFit(
    sheet: IntSize,
    window: IntSize,
    horizontalPadding: Int,
    verticalPadding: Int,
    hasNotes: Boolean = false
): ChordSheetFit {
    val landscape = sheet.width > sheet.height
    val sideways = landscape && window.width < window.height
    val viewport = if (sideways) IntSize(window.height, window.width) else window
    val widthScale = (viewport.width - horizontalPadding - 2).toFloat() / sheet.width
    val heightScale = (viewport.height - verticalPadding - 2).toFloat() / sheet.height
    // Give wide sheets with extra verses more room to fit a short landscape window.
    val minimumScale = if (landscape && hasNotes) 0.65f else 0.8f
    return ChordSheetFit(landscape, sideways, viewport, minOf(widthScale, heightScale).coerceAtLeast(minimumScale))
}

@Composable
private fun ChordSheetPage(sheet: ChordSheet, width: Dp, scale: Float, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val rowStyle = chordRowStyle.scaled(density, scale)
    val asideStyle = chordAsideStyle.scaled(density, scale)
    val noteStyle = chordNoteStyle.scaled(density, scale)
    val columnGap = chordColumnGap * scale
    val lyricOverlap = chordLyricOverlap * scale
    val paragraphGap = chordNoteParagraphGap * scale
    Column(modifier) {
        ChordSheetHeading(sheet, width, scale)
        Spacer(Modifier.height(12.dp * scale))
        sheet.lines.forEachIndexed { index, line ->
            val nextKind = sheet.lines.getOrNull(index + 1)?.kind
            val isNote = line.kind == "note"
            val startsNote = isNote && (line.paragraphStart ?: (sheet.lines.getOrNull(index - 1)?.kind != "note"))
            val endsNotes = isNote && nextKind != "note"
            val style = if (isNote) {
                with(density) {
                    val asideWidth = if (line.aside.isEmpty()) 0 else {
                        columnGap.roundToPx() + measurer.measure(line.aside, asideStyle, softWrap = false).size.width
                    }
                    val textWidth = measurer.measure(line.text, noteStyle, softWrap = false).size.width
                    val textScale = fitChordTextScale(width.roundToPx() - asideWidth, textWidth)
                    noteStyle.scaled(density, textScale).copy(lineHeight = noteStyle.lineHeight)
                }
            } else {
                rowStyle
            }
            val rowSpacing = if (line.kind == "chords" && nextKind == "lyrics") {
                // Tighten the space below this row while preserving its baseline and text size.
                Modifier.layout { measurable, constraints ->
                    val row = measurable.measure(constraints)
                    val height = constraints.constrainHeight(row.height - lyricOverlap.roundToPx())
                    layout(row.width, height) { row.placeRelative(0, 0) }
                }
            } else {
                Modifier
            }
            Row(
                modifier = Modifier.fillMaxWidth().then(rowSpacing).padding(
                    top = if (startsNote) paragraphGap else 0.dp,
                    bottom = if (endsNotes) paragraphGap else 0.dp
                ),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (line.text.isEmpty()) AnnotatedString(" ") else line.text,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                    color = chordSheetColor(line.kind),
                    style = style,
                    softWrap = false
                )
                if (line.aside.isNotEmpty()) {
                    Text(
                        line.aside,
                        modifier = Modifier.padding(start = columnGap).alignByBaseline(),
                        color = if (line.asideTone == "pitch") Color(0xFFBF8F00) else Color(0xFF5F5537),
                        style = asideStyle,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ChordSheetHeading(sheet: ChordSheet, width: Dp, scale: Float) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val (credits, capo) = sheet.headingDetails
    val creditText = remember(credits) {
        buildAnnotatedString {
            credits.forEachIndexed { index, detail ->
                if (index > 0) append(" · ")
                append(detail)
            }
        }
    }
    val capoText = capo.joinToString(" · ")
    val baseTitleStyle = chordTitleStyle.scaled(density, scale)
    val baseDetailsStyle = chordDetailsStyle.scaled(density, scale)
    val baseGap = 10.dp * scale
    val headingScale = with(density) {
        val titleWidth = measurer.measure(sheet.title, baseTitleStyle, softWrap = false).size.width
        val creditsWidth = measurer.measure(creditText, baseDetailsStyle, softWrap = false).size.width
        val capoWidth = measurer.measure(capoText, baseDetailsStyle, softWrap = false).size.width
        val gaps = if (capoText.isEmpty()) 1 else 2
        val needed = titleWidth + creditsWidth + capoWidth + gaps * baseGap.roundToPx()
        fitChordTextScale(width.roundToPx(), needed)
    }
    val detailsStyle = baseDetailsStyle.scaled(density, headingScale)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(baseGap * headingScale)) {
        Text(
            sheet.title,
            Modifier.alignByBaseline(),
            color = SheetInk,
            style = baseTitleStyle.scaled(density, headingScale),
            maxLines = 1,
            softWrap = false
        )
        if (creditText.isEmpty()) {
            Spacer(Modifier.weight(1f))
        } else {
            Text(
                creditText,
                Modifier.weight(1f).alignByBaseline(),
                color = chordSheetColor("detail"),
                style = detailsStyle,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false
            )
        }
        if (capoText.isNotEmpty()) {
            Text(
                capoText,
                Modifier.alignByBaseline(),
                color = chordSheetColor("detail"),
                style = detailsStyle,
                textAlign = TextAlign.End,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

internal fun fitChordTextScale(availableWidth: Int, textWidth: Int): Float =
    if (textWidth <= availableWidth) 1f else (availableWidth - 2).coerceAtLeast(1).toFloat() / textWidth.coerceAtLeast(1)

private fun TextStyle.scaled(density: Density, scale: Float): TextStyle = with(density) {
    copy(fontSize = (fontSize.toPx() * scale).toSp(), lineHeight = (lineHeight.toPx() * scale).toSp())
}
