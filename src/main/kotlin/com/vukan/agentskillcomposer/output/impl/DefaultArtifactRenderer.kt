package com.vukan.agentskillcomposer.output.impl

import com.vukan.agentskillcomposer.model.GeneratedArtifact
import com.vukan.agentskillcomposer.output.ArtifactMetadata
import com.vukan.agentskillcomposer.output.ArtifactRenderer

class DefaultArtifactRenderer : ArtifactRenderer {

    override fun render(artifact: GeneratedArtifact, metadata: ArtifactMetadata): String {
        val body = artifact.content.trimEnd()
        val composed = if (metadata.requiresFrontmatter) {
            val frontmatter = metadata.frontmatterTemplate
                ?: error("requiresFrontmatter=true but frontmatterTemplate is null for ${metadata.relativePath}")
            "$frontmatter\n\n$body"
        } else {
            body
        }
        return "$composed\n"
    }
}
