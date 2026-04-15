package com.vukan.agentskillcomposer.model

import java.nio.file.Path

data class ProjectFacts(
    val projectRoot: Path,
    val languages: Set<ProjectLanguage>,
    val buildSystem: BuildSystem?,
    val frameworks: List<DetectedFramework>,
    val testFrameworks: List<TestFramework>,
    val sourceRoots: List<Path>,
    val testRoots: List<Path>,
    val conventions: List<DetectedConvention>,
    val representativeFiles: List<RepresentativeFile>,
    val suggestedSkills: List<SkillSuggestion> = emptyList(),
) {
    val projectName: String get() = projectRoot.fileName?.toString() ?: "Unknown"
}
