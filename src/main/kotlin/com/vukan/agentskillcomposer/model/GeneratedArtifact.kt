package com.vukan.agentskillcomposer.model

data class GeneratedArtifact(
    val target: GenerationTarget,
    val artifactType: ArtifactType,
    val title: String,
    val content: String,
    val defaultPath: String,
) {
    companion object {
        fun placeholder(target: GenerationTarget, artifactType: ArtifactType): GeneratedArtifact =
            GeneratedArtifact(
                target = target,
                artifactType = artifactType,
                title = "${artifactType.displayName} for ${target.displayName}",
                content = "",
                defaultPath = "",
            )
    }
}
