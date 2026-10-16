package xyz.tosdev.yetasimplelyicsapp.domain.lyrics.parser

import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsDocument
import xyz.tosdev.yetasimplelyicsapp.domain.lyrics.LyricsSource

data class LyricsParseContext(
    val documentId: String,
    val trackKey: String,
    val source: LyricsSource,
    val languageTag: String? = null,
) {
    init {
        require(documentId.isNotBlank()) { "documentId must not be blank" }
        require(trackKey.isNotBlank()) { "trackKey must not be blank" }
    }
}

enum class ParseDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

data class ParseDiagnostic(
    val severity: ParseDiagnosticSeverity,
    val code: String,
    val message: String,
    val line: Int? = null,
    val column: Int? = null,
) {
    init {
        require(code.isNotBlank()) { "diagnostic code must not be blank" }
    }
}

data class LyricsParseResult(
    val document: LyricsDocument?,
    val diagnostics: List<ParseDiagnostic> = emptyList(),
) {
    val isSuccess: Boolean
        get() = document != null && diagnostics.none { it.severity == ParseDiagnosticSeverity.ERROR }
}

fun interface LyricsTextParser {
    fun parse(input: String, context: LyricsParseContext): LyricsParseResult
}
