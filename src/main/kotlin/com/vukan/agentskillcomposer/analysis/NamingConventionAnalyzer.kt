package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.vfs.VirtualFile
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention

class NamingConventionAnalyzer {

    fun analyze(
        sourceFiles: List<VirtualFile>,
        testFiles: List<VirtualFile>,
    ): List<DetectedConvention> {
        val sourceConventions = detectPatterns(sourceFiles, SOURCE_PATTERNS, ConventionType.NAMING)
        val testConventions = detectPatterns(testFiles, TEST_PATTERNS, ConventionType.NAMING)
        return sourceConventions + testConventions
    }

    private fun detectPatterns(
        files: List<VirtualFile>,
        patterns: List<NamingPattern>,
        type: ConventionType,
    ): List<DetectedConvention> = patterns.mapNotNull { pattern ->
        val matching = files.filter { pattern.regex.matches(it.nameWithoutExtension) }
        val confidence = when {
            matching.size >= 3 -> ConventionConfidence.HIGH
            matching.size == 2 -> ConventionConfidence.MEDIUM
            else -> return@mapNotNull null
        }
        DetectedConvention(
            type = type,
            summary = pattern.summary,
            confidence = confidence,
            evidence = matching.take(MAX_EVIDENCE).map { it.name },
        )
    }

    private data class NamingPattern(
        val regex: Regex,
        val summary: String,
    )

    companion object {
        private const val MAX_EVIDENCE = 5

        private val SOURCE_PATTERNS = listOf(
            NamingPattern(Regex(".+Service"), "Service classes use *Service suffix"),
            NamingPattern(Regex(".+Repository"), "Repository interfaces use *Repository suffix"),
            NamingPattern(Regex(".+Controller"), "Controller classes use *Controller suffix"),
            NamingPattern(Regex(".+Handler"), "Handler classes use *Handler suffix"),
            NamingPattern(Regex(".+Mapper"), "Mapper classes use *Mapper suffix"),
            NamingPattern(Regex(".+(Dto|DTO)"), "DTOs use *Dto/*DTO suffix"),
            NamingPattern(Regex(".+(Entity)"), "Entity classes use *Entity suffix"),
            NamingPattern(Regex(".+(Config|Configuration)"), "Configuration classes use *Config/*Configuration suffix"),
        )

        private val TEST_PATTERNS = listOf(
            NamingPattern(Regex(".+Test"), "Test classes use *Test suffix"),
            NamingPattern(Regex(".+Tests"), "Test classes use *Tests suffix"),
            NamingPattern(Regex(".+Spec"), "Test classes use *Spec suffix (Kotest style)"),
            NamingPattern(Regex(".+IT"), "Integration tests use *IT suffix"),
        )
    }
}
