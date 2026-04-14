package com.vukan.agentskillcomposer.output

import com.vukan.agentskillcomposer.model.GeneratedArtifact

interface ArtifactRenderer {
    fun render(artifact: GeneratedArtifact, metadata: ArtifactMetadata): String
}
