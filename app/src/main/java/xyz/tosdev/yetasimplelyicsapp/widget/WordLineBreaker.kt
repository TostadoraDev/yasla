package xyz.tosdev.yetasimplelyicsapp.widget

import java.util.Locale

internal data class WidgetTextLayout(
    val text: String,
    val fontSizeSp: Float,
    val lineCount: Int,
    val ellipsized: Boolean,
) {
    val visualLines: List<String>
        get() = text.split('\n')
}

internal fun interface WidgetTextMeasurer {
    fun measureWidthDp(text: String, fontSizeSp: Float): Float

    /** Actual renderer metrics when available; NaN selects the density-aware fallback. */
    fun measureLineHeightDp(fontSizeSp: Float): Float = Float.NaN
}

internal fun currentWordLineCapacity(
    availableHeightDp: Float,
    minimumFontSizeSp: Float,
    fontScaleFactor: Float,
    lineHeightRatio: Float = 1.2f,
): Int {
    val lineHeightDp = minimumFontSizeSp.coerceAtLeast(1f) *
        fontScaleFactor.coerceAtLeast(0.5f) *
        lineHeightRatio.coerceAtLeast(1f)
    return (availableHeightDp.coerceAtLeast(1f) / lineHeightDp)
        .toInt()
        .coerceAtLeast(CURRENT_WORD_MIN_VISUAL_LINES)
}

/**
 * Presentation-only fitting for a complete semantic word. Timing segments are intentionally not
 * accepted by this API, so TTML synchronization boundaries cannot become visual line breaks.
 */
internal object WordLineBreaker {
    fun layoutWord(
        word: String,
        languageTag: String?,
        fallbackLocale: Locale,
        availableWidthDp: Float,
        fontScaleFactor: Float = 1f,
        preferredFontSizeSp: Float,
        minFontSizeSp: Float,
        maxLines: Int,
        textMeasurer: WidgetTextMeasurer? = null,
        availableHeightDp: Float = Float.POSITIVE_INFINITY,
        lineHeightRatio: Float = 1.25f,
    ): WidgetTextLayout {
        val locale = languageTag?.takeIf(String::isNotBlank)?.let(Locale::forLanguageTag)
            ?.takeUnless { it.language.isBlank() } ?: fallbackLocale
        val displayWord = word.trim().uppercase(locale)
        val preferred = preferredFontSizeSp.coerceAtLeast(1f)
        if (displayWord.isEmpty()) return WidgetTextLayout("", preferred, 1, false)
        val width = availableWidthDp.coerceAtLeast(0.1f)
        val height = availableHeightDp.coerceAtLeast(0.1f)
        val scale = fontScaleFactor.coerceAtLeast(0.5f)
        val measurer = textMeasurer ?: WidgetTextMeasurer { text, size ->
            estimatedWidth(text, size) * scale
        }
        val minimum = minFontSizeSp.coerceIn(1f, preferred)
        val graphemes = splitIntoGraphemes(displayWord)
        // Linguistic cuts are a small preference, never a restriction. The old syllable-first
        // return accepted an unbalanced QUIE/RE before considering the better QUI/ERE.
        val linguisticCuts = buildSet {
            var offset = 0
            SyllableBreaker.breakWord(displayWord, locale).dropLast(1).forEach {
                offset += it.length
                add(offset)
            }
        }
        fun lineHeight(size: Float): Float = measurer.measureLineHeightDp(size)
            .takeIf { it.isFinite() && it > 0f }
            ?: (size * scale * lineHeightRatio.coerceAtLeast(1f))
        fun result(visualLines: List<String>, size: Float) = WidgetTextLayout(
            text = visualLines.joinToString("\n"), fontSizeSp = size,
            lineCount = visualLines.size, ellipsized = false,
        )
        fun oneLine(size: Float): WidgetTextLayout? =
            if (lineHeight(size) <= height && measurer.measureWidthDp(displayWord, size) <= width) {
                result(listOf(displayWord), size)
            } else null
        fun wrapped(size: Float, requiredLines: Int? = null): WidgetTextLayout? {
            val capacity = minOf(maxLines.coerceAtLeast(1), (height / lineHeight(size))
                .coerceAtMost(graphemes.size.toFloat()).toInt())
            if (requiredLines != null && requiredLines > capacity) return null
            return balancedPartition(graphemes, linguisticCuts, width, size,
                requiredLines ?: capacity, measurer, requiredLines ?: 2)
                ?.let { result(it, size) }
        }

        // Preserve a complete one-line word before introducing any visual break.
        var size = preferred
        while (size >= minimum) {
            oneLine(size)?.let { return it }
            if (size == minimum) break
            size = (size - 1f).coerceAtLeast(minimum)
        }
        // Prefer fewer balanced lines within the moderate range over a larger font that
        // forces extra fragments or isolated letters (for example DO/B/LA).
        for (lineCount in 2..minOf(maxLines, graphemes.size)) {
            size = preferred
            while (size >= minimum) {
                wrapped(size, lineCount)?.let { return it }
                if (size == minimum) break
                size = (size - 1f).coerceAtLeast(minimum)
            }
        }
        // Very small launcher rectangles: show every grapheme, without ellipsis. Height is
        // checked at every size, not inferred from a maxLines value from another rectangle.
        size = minimum - 0.5f
        while (size >= CURRENT_WORD_HARD_FLOOR_SP) {
            oneLine(size)?.let { return it }
            wrapped(size)?.let { return it }
            size -= 0.5f
        }
        // Physically impossible bounds or an inconsistent injected measurer still retain all
        // source text. Real Paint metrics scale with the font and normally fit above this floor.
        return result(graphemes.chunked((graphemes.size + maxLines.coerceAtLeast(1) - 1) /
            maxLines.coerceAtLeast(1)).map { it.joinToString("") }, CURRENT_WORD_HARD_FLOOR_SP)
    }

    @Suppress("UNUSED_PARAMETER")
    fun layoutLine(
        line: String,
        locale: Locale,
        availableWidthDp: Float,
        availableHeightDp: Float = Float.POSITIVE_INFINITY,
        fontScaleFactor: Float = 1f,
        preferredFontSizeSp: Float,
        minFontSizeSp: Float,
        maxLines: Int,
        fontWeightWidthFactor: Float = 1.06f,
        lineHeightRatio: Float = 1.20f,
    ): WidgetTextLayout {
        val normalized = line.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) {
            return WidgetTextLayout("", preferredFontSizeSp, 1, ellipsized = false)
        }
        val scale = fontScaleFactor.coerceAtLeast(0.5f)
        val width = availableWidthDp.coerceAtLeast(1f) / scale
        val height = availableHeightDp.coerceAtLeast(1f) / scale
        val minimum = minFontSizeSp.coerceAtLeast(8f)
        val lineLimit = maxLines.coerceAtLeast(1)
        val weightFactor = fontWeightWidthFactor.coerceAtLeast(1f)
        val heightRatio = lineHeightRatio.coerceIn(1f, 1.5f)
        var size = preferredFontSizeSp.coerceAtLeast(minimum)
        while (size >= minimum) {
            val wrapped = wrapAtWords(normalized, width, size, weightFactor)
            val textHeight = wrapped.size * size * heightRatio
            if (
                wrapped.size <= lineLimit &&
                textHeight <= height &&
                wrapped.all { estimatedWidth(it, size) * weightFactor <= width }
            ) {
                return WidgetTextLayout(wrapped.joinToString("\n"), size, wrapped.size, false)
            }
            size -= 1f
        }

        val wrapped = wrapAtWords(normalized, width, minimum, weightFactor)
        val heightLimitedLines = (height / (minimum * heightRatio)).toInt().coerceAtLeast(1)
        val visibleLimit = minOf(lineLimit, heightLimitedLines)
        val visible = wrapped.take(visibleLimit).toMutableList()
        val omitted = wrapped.size > visibleLimit
        if (visible.isEmpty()) visible += normalized
        val lastIndex = visible.lastIndex
        if (omitted || estimatedWidth(visible[lastIndex], minimum) * weightFactor > width) {
            val lastSource = if (omitted) {
                wrapped.drop(lastIndex).joinToString(" ")
            } else {
                visible[lastIndex]
            }
            visible[lastIndex] = ellipsize(
                lastSource,
                width / weightFactor,
                minimum,
            )
        }
        return WidgetTextLayout(
            text = visible.joinToString("\n"),
            fontSizeSp = minimum,
            lineCount = visible.size,
            ellipsized = omitted || visible.joinToString(" ") != normalized,
        )
    }

    /** Minimum-width-variance partition with a strong penalty for isolated graphemes. */
    private fun balancedPartition(
        units: List<String>,
        linguisticCuts: Set<Int>,
        width: Float,
        fontSize: Float,
        maxLines: Int,
        measurer: WidgetTextMeasurer,
        minLines: Int = 2,
    ): List<String>? {
        if (maxLines < 2 || units.size < 2) return null
        data class Candidate(val lines: List<String>, val score: Double)
        val source = units.joinToString("")
        val offsets = IntArray(units.size + 1)
        units.forEachIndexed { index, unit -> offsets[index + 1] = offsets[index] + unit.length }
        val widths = mutableMapOf<Pair<Int, Int>, Float>()
        val results = mutableMapOf<Pair<Int, Int>, Candidate?>()
        fun fragment(start: Int, end: Int) = source.substring(offsets[start], offsets[end])
        fun measured(start: Int, end: Int) = widths.getOrPut(start to end) {
            measurer.measureWidthDp(fragment(start, end), fontSize)
        }
        fun cost(start: Int, end: Int, measuredWidth: Float): Double {
            val slack = (1.0 - measuredWidth / width).coerceAtLeast(0.0)
            val orphan = if (end - start == 1 && units.size >= 4) 2.0 else 0.0
            val linguistic = if (end < units.size && offsets[end] !in linguisticCuts) 0.015 else 0.0
            return slack * slack + orphan + linguistic
        }
        fun solve(start: Int, remaining: Int): Candidate? {
            val key = start to remaining
            if (results.containsKey(key)) return results[key]
            if (units.size - start < remaining) return null
            if (remaining == 1) {
                val length = measured(start, units.size)
                return (if (length <= width) Candidate(listOf(fragment(start, units.size)),
                    cost(start, units.size, length)) else null).also { results[key] = it }
            }
            var best: Candidate? = null
            for (end in start + 1..units.size - remaining + 1) {
                val length = measured(start, end)
                if (length > width) continue
                val tail = solve(end, remaining - 1) ?: continue
                val score = cost(start, end, length) + tail.score
                if (best == null || score < best.score) best = Candidate(
                    listOf(fragment(start, end)) + tail.lines, score)
            }
            results[key] = best
            return best
        }
        for (lineCount in minLines..minOf(maxLines, units.size)) {
            solve(0, lineCount)?.let { return it.lines }
        }
        return null
    }

    // Android and JVM Pattern implement Unicode extended graphemes. UTF-16 offsets from these
    // matches cannot split combining marks, surrogate pairs, emoji modifiers, flags or ZWJ emoji.
    private val graphemePattern = java.util.regex.Pattern.compile("\\X")

    internal fun splitIntoGraphemes(text: String): List<String> {
        val matcher = graphemePattern.matcher(text)
        return buildList { while (matcher.find()) add(matcher.group()) }
    }

    private fun wrapAtWords(
        text: String,
        width: Float,
        fontSize: Float,
        widthFactor: Float,
    ): List<String> {
        val result = mutableListOf<String>()
        var current = ""
        text.split(' ').filter(String::isNotEmpty).forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (
                current.isNotEmpty() &&
                estimatedWidth(candidate, fontSize) * widthFactor > width
            ) {
                result += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    private fun ellipsize(
        text: String,
        width: Float,
        fontSize: Float,
        measureWidth: WidgetTextMeasurer = WidgetTextMeasurer(::estimatedWidth),
    ): String {
        if (measureWidth.measureWidthDp(text, fontSize) <= width) return text
        var candidate = text
        while (
            candidate.isNotEmpty() &&
            measureWidth.measureWidthDp("$candidate…", fontSize) > width
        ) {
            candidate = candidate.dropLast(1).trimEnd()
        }
        return if (candidate.isEmpty()) "…" else "$candidate…"
    }

    private fun estimatedWidth(text: String, fontSize: Float): Float =
        text.sumOf { character -> characterWidthFactor(character).toDouble() }.toFloat() * fontSize

    private fun characterWidthFactor(character: Char): Float = when {
        character == '\n' -> 0f
        character.isWhitespace() -> 0.32f
        character in "MWÁÉÓÑÜ" -> 0.84f
        character in "IÍÌÎÏJL" -> 0.34f
        character in "'’.,:;!¡?¿" -> 0.30f
        character == '-' -> 0.38f
        character.code >= 0x2E80 -> 1.0f
        else -> 0.60f
    }
}

private const val CURRENT_WORD_MIN_VISUAL_LINES = 2
private const val CURRENT_WORD_HARD_FLOOR_SP = 0.1f

internal object SyllableBreaker {
    private val supportedLanguages = setOf("es", "en", "fr", "it", "pt")
    private val commonOnsetClusters = setOf(
        "bl", "br", "ch", "cl", "cr", "dr", "fl", "fr", "gl", "gr", "pl",
        "pr", "tr",
    )
    private val onsetClustersByLanguage = mapOf(
        "es" to commonOnsetClusters + setOf("ll", "rr"),
        "en" to commonOnsetClusters + setOf(
            "ph", "sh", "sk", "sl", "sm", "sn", "sp", "st", "sw", "th", "tw", "wh",
        ),
        "fr" to commonOnsetClusters + setOf("gn", "ph", "qu"),
        "it" to commonOnsetClusters + setOf("gn", "sc"),
        "pt" to commonOnsetClusters + setOf("lh", "nh", "qu", "rr"),
    )

    fun breakWord(word: String, locale: Locale): List<String> {
        val language = locale.language.lowercase(Locale.ROOT)
        if (word.length < 3 || language !in supportedLanguages) {
            return listOf(word)
        }
        // Contractions stay intact because an apostrophe is not a safe syllable boundary.
        if (word.any { it == '\'' || it == '’' }) return listOf(word)

        if ('-' in word) {
            val parts = word.split('-')
            if (parts.any(String::isEmpty)) return listOf(word)
            return buildList {
                parts.forEachIndexed { index, part ->
                    val partSyllables = syllabifyAffixedWord(part, language)
                    partSyllables.forEachIndexed { syllableIndex, syllable ->
                        val isPartEnd = syllableIndex == partSyllables.lastIndex
                        add(if (isPartEnd && index < parts.lastIndex) "$syllable-" else syllable)
                    }
                }
            }
        }

        return syllabifyAffixedWord(word, language)
    }

    private fun syllabifyAffixedWord(word: String, language: String): List<String> {
        val firstLetter = word.indexOfFirst(Char::isLetter)
        val lastLetter = word.indexOfLast(Char::isLetter)
        if (firstLetter < 0 || lastLetter < firstLetter) return listOf(word)
        val prefix = word.substring(0, firstLetter)
        val core = word.substring(firstLetter, lastLetter + 1)
        val suffix = word.substring(lastLetter + 1)
        val coreSyllables = syllabify(core, language)
        if (coreSyllables.size <= 1) return listOf(word)

        return coreSyllables.mapIndexed { index, syllable ->
            buildString {
                if (index == 0) append(prefix)
                append(syllable)
                if (index == coreSyllables.lastIndex) append(suffix)
            }
        }
    }

    private fun syllabify(word: String, language: String): List<String> {
        val nuclei = mutableListOf<IntRange>()
        var index = 0
        while (index < word.length) {
            if (!word[index].isVowel()) {
                index++
                continue
            }
            val start = index
            var end = index
            while (
                end + 1 < word.length &&
                word[end + 1].isVowel() &&
                formsDiphthong(word[end], word[end + 1], language)
            ) {
                end++
            }
            nuclei += start..end
            index = end + 1
        }
        if (nuclei.size <= 1) return listOf(word)

        val boundaries = mutableListOf<Int>()
        nuclei.zipWithNext().forEach { (left, right) ->
            val gapStart = left.last + 1
            val gapLength = right.first - gapStart
            val boundary = when {
                gapLength <= 0 -> right.first
                gapLength == 1 -> gapStart
                gapLength == 2 && word.substring(gapStart, right.first)
                    .lowercase(Locale.ROOT) in onsetClusters(language) ->
                    gapStart

                gapLength >= 2 && word.substring(right.first - 2, right.first)
                    .lowercase(Locale.ROOT) in onsetClusters(language) ->
                    right.first - 2

                else -> right.first - 1
            }
            if (boundary in 1 until word.length) boundaries += boundary
        }

        if (boundaries.isEmpty()) return listOf(word)
        val syllables = mutableListOf<String>()
        var start = 0
        boundaries.distinct().sorted().forEach { boundary ->
            if (boundary > start) syllables += word.substring(start, boundary)
            start = boundary
        }
        if (start < word.length) syllables += word.substring(start)
        return syllables.filter(String::isNotEmpty)
    }

    private fun Char.isVowel(): Boolean = lowercaseChar() in "aeiouáéíóúàèìòùâêîôûäëïöü"

    private fun onsetClusters(language: String): Set<String> =
        onsetClustersByLanguage[language].orEmpty()

    private fun formsDiphthong(left: Char, right: Char, language: String): Boolean {
        if (language != "es") return true
        val leftLower = left.lowercaseChar()
        val rightLower = right.lowercaseChar()
        if (leftLower in "íú" || rightLower in "íú") return false
        val strong = "aáeéoó"
        return leftLower !in strong || rightLower !in strong
    }
}
