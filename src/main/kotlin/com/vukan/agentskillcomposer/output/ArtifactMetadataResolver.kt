package com.vukan.agentskillcomposer.output

import com.vukan.agentskillcomposer.model.ArtifactType
import com.vukan.agentskillcomposer.model.GenerationTarget
import java.nio.file.Path

class ArtifactMetadataResolver(private val pathResolver: TargetPathResolver) {

    fun resolve(target: GenerationTarget, artifactType: ArtifactType): ArtifactMetadata {
        val relativePath = pathResolver.resolveRelativePath(target, artifactType)
        val fileName = Path.of(relativePath).fileName.toString()

        val (requiresFrontmatter, template) = when {
            target == GenerationTarget.JUNIE && artifactType is ArtifactType.Skill -> {
                val suggestion = artifactType.suggestion
                true to buildJunieFrontmatter(suggestion.displayName, suggestion.description)
            }
            else -> false to null
        }

        return ArtifactMetadata(
            fileName = fileName,
            relativePath = relativePath,
            requiresFrontmatter = requiresFrontmatter,
            frontmatterTemplate = template,
        )
    }

    private fun buildJunieFrontmatter(name: String, description: String): String =
        """
        |---
        |name: ${yamlQuote(name)}
        |description: ${yamlQuote(description)}
        |---
        """.trimMargin()

    private fun yamlQuote(value: String): String =
        if (value.contains(Regex("[:#{}\\[\\],&*?|>!'\"\\n]"))) {
            "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } else {
            value
        }
}
