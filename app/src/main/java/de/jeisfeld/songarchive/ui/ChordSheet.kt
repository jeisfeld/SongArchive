package de.jeisfeld.songarchive.ui

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.verticalScrollAxisRange
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jeisfeld.songarchive.R
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
                val version = sheet.opt("version") as? Number
                if (sheet.opt("enabled") == false || version?.toDouble() != 1.0) return null
                val title = sheet.opt("title") as? String ?: return null
                val details = sheet.getJSONArray("details")
                val rows = sheet.getJSONArray("lines")
                val lines = List(rows.length()) { index ->
                    val row = rows.getJSONObject(index)
                    val kind = row.getString("kind")
                    if (kind !in chordLineKinds) return null
                    val aside = if (row.isNull("aside")) "" else row.opt("aside") as? String ?: return null
                    val runs = row.getJSONArray("runs")
                    val text = buildAnnotatedString {
                        repeat(runs.length()) { runIndex ->
                            val run = runs.getJSONObject(runIndex)
                            val runText = run.opt("text") as? String ?: return null
                            withStyle(
                                SpanStyle(
                                    color = chordSheetColor(run.optString("tone"), Color.Unspecified),
                                    fontWeight = FontWeight.Bold.takeIf { run.optBoolean("bold") },
                                    fontStyle = FontStyle.Italic.takeIf { run.optBoolean("italic") },
                                    textDecoration = TextDecoration.Underline.takeIf { run.optString("underline").isNotEmpty() },
                                    baselineShift = BaselineShift.Superscript.takeIf { run.optBoolean("superscript") }
                                )
                            ) {
                                append(runText)
                            }
                        }
                    }
                    ChordSheetLine(
                        kind, text, aside, row.optString("asideTone"),
                        row.opt("paragraphStart") as? Boolean
                    )
                }
                if (lines.none { it.kind == "chords" && (it.text.isNotBlank() || it.aside.isNotBlank()) }) return null
                val headingRuns = sheet.optJSONArray("headingRuns")
                ChordSheet(
                    readHeadingText(title, headingRuns?.optJSONArray(0)),
                    chordSheetHeadingDetails(List(details.length()) {
                        val detail = details.opt(it) as? String ?: return null
                        readHeadingText(detail, headingRuns?.optJSONArray(it + 1))
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
    val sidePadding = dimensionResource(R.dimen.chord_sheet_side_padding)
    val topPadding = 48.dp
    val bottomPadding = dimensionResource(R.dimen.chord_sheet_bottom_padding)

    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val naturalPageWidth = naturalWidth.toDp()
        val natural = subcompose("natural") { ChordSheetPage(sheet, naturalPageWidth, 1f) }
            .single().measure(Constraints.fixedWidth(naturalWidth))
        val window = IntSize(constraints.maxWidth, constraints.maxHeight)
        val verticalPadding = topPadding.roundToPx() + bottomPadding.roundToPx()
        val fit = chordSheetFit(
            sheet = IntSize(natural.width, natural.height),
            window = window,
            horizontalPadding = 2 * sidePadding.roundToPx(),
            verticalPadding = verticalPadding,
            hasNotes = hasNotes
        )
        val sheetWidth = ceil(naturalWidth * fit.scale).toInt().toDp()
        val landscapeHeight = if (fit.landscape) fit.viewport.height - verticalPadding else 0
        val page = subcompose("fitted") {
            LaunchedEffect(fit.landscape) { onLandscapeSheet(fit.landscape) }
            PannableChordSheet(
                sheet, sheetWidth, fit, landscapeHeight,
                Modifier.padding(
                    start = sidePadding, end = sidePadding, top = topPadding, bottom = bottomPadding
                )
            )
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

@Composable
private fun PannableChordSheet(sheet: ChordSheet, width: Dp, fit: ChordSheetFit, landscapeHeight: Int, modifier: Modifier) {
    var transform by remember(sheet, fit) { mutableStateOf(ChordSheetTransform()) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    Layout(
        content = {
            ChordSheetPage(
                sheet, width, fit.scale, Modifier.onSizeChanged { pageSize = it },
                landscapeHeight = landscapeHeight
            )
        },
        modifier = modifier.fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewportSize = it }
            .pointerInput(sheet, fit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    transform = transform.update(pageSize, size, centroid, pan, zoom)
                }
            }
            .semantics {
                horizontalScrollAxisRange = ScrollAxisRange(
                    value = { (-transform.origin(pageSize, viewportSize).x).coerceAtLeast(0f) },
                    maxValue = { (pageSize.width * transform.scale - viewportSize.width).coerceAtLeast(0f) }
                )
                verticalScrollAxisRange = ScrollAxisRange(
                    value = { (-transform.origin(pageSize, viewportSize).y).coerceAtLeast(0f) },
                    maxValue = { (pageSize.height * transform.scale - viewportSize.height).coerceAtLeast(0f) }
                )
                scrollBy { x, y ->
                    val previous = transform.origin(pageSize, viewportSize)
                    transform = transform.update(pageSize, viewportSize, Offset.Zero, Offset(-x, -y), 1f)
                    transform.origin(pageSize, viewportSize) != previous
                }
            }
    ) { measurables, constraints ->
        val viewport = IntSize(constraints.maxWidth, constraints.maxHeight)
        val page = measurables.single().measure(Constraints.fixedWidth(width.roundToPx()))
        val measuredSize = IntSize(page.width, page.height)
        layout(viewport.width, viewport.height) {
            page.placeWithLayer(0, 0) {
                val origin = transform.origin(measuredSize, viewport)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = transform.scale
                scaleY = transform.scale
                translationX = origin.x
                translationY = origin.y
            }
        }
    }
}

internal data class ChordSheetTransform(val scale: Float = 1f, private val offset: Offset = Offset.Zero) {
    fun origin(page: IntSize, viewport: IntSize): Offset = boundOrigin(offset, page, viewport, scale)

    fun update(page: IntSize, viewport: IntSize, centroid: Offset, pan: Offset, zoom: Float): ChordSheetTransform {
        val nextScale = (scale * zoom).coerceIn(0.5f, 5f)
        val nextOrigin = centroid + pan + (origin(page, viewport) - centroid) * (nextScale / scale)
        return ChordSheetTransform(nextScale, boundOrigin(nextOrigin, page, viewport, nextScale))
    }

    private fun boundOrigin(origin: Offset, page: IntSize, viewport: IntSize, scale: Float): Offset {
        fun coordinate(offset: Float, space: Float): Float = if (space >= 0) space / 2 else offset.coerceIn(space, 0f)
        return Offset(
            coordinate(origin.x, viewport.width - page.width * scale),
            coordinate(origin.y, viewport.height - page.height * scale)
        )
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

internal fun chordPairGap(availableHeight: Int, sheetHeight: Int, pairCount: Int, maxGap: Int): Int =
    if (pairCount == 0) 0 else minOf(maxGap, (availableHeight - sheetHeight - 2).coerceAtLeast(0) / pairCount)

@Composable
private fun ChordSheetPage(
    sheet: ChordSheet,
    width: Dp,
    scale: Float,
    modifier: Modifier = Modifier,
    landscapeHeight: Int = 0
) {
    val density = LocalDensity.current
    val rowStyle = chordRowStyle.scaled(density, scale)
    val asideStyle = chordAsideStyle.scaled(density, scale)
    val noteStyle = chordNoteStyle.scaled(density, scale).copy(color = chordSheetColor("note"))
    val noteAutoSize = TextAutoSize.StepBased(minFontSize = 0.1.sp, maxFontSize = noteStyle.fontSize, stepSize = 0.1.sp)
    val columnGap = chordColumnGap * scale
    val lyricOverlap = chordLyricOverlap * scale
    val paragraphGap = chordNoteParagraphGap * scale
    val chordPairStarts = remember(sheet) {
        sheet.lines.indices.filter { index ->
            sheet.lines[index].kind == "chords" && sheet.lines.getOrNull(index - 1)?.kind == "lyrics"
        }.toSet()
    }
    Layout(
        modifier = modifier,
        content = {
            ChordSheetHeading(sheet, width, scale)
            Spacer(Modifier.height(12.dp * scale))
            sheet.lines.forEachIndexed { index, line ->
                val nextKind = sheet.lines.getOrNull(index + 1)?.kind
                val isNote = line.kind == "note"
                val startsNote = isNote && (line.paragraphStart ?: (sheet.lines.getOrNull(index - 1)?.kind != "note"))
                val endsNotes = isNote && nextKind != "note"
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
                    val text = if (line.text.isEmpty()) AnnotatedString(" ") else line.text
                    val textModifier = Modifier.weight(1f).alignByBaseline()
                    if (isNote) {
                        BasicText(
                            text = text,
                            modifier = textModifier.padding(end = 2.dp * scale),
                            style = noteStyle,
                            autoSize = noteAutoSize,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip
                        )
                    } else {
                        Text(text, textModifier, color = chordSheetColor(line.kind), style = rowStyle, softWrap = false)
                    }
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
    ) { measurables, constraints ->
        val rows = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val height = rows.sumOf { it.height }
        val extraGap = chordPairGap(landscapeHeight, height, chordPairStarts.size, (6.dp * scale).roundToPx())
        layout(constraints.maxWidth, height + extraGap * chordPairStarts.size) {
            var top = 0
            rows.forEachIndexed { index, row ->
                // The heading and its spacer come before the song rows.
                if (index - 2 in chordPairStarts) top += extraGap
                row.placeRelative(0, top)
                top += row.height
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
