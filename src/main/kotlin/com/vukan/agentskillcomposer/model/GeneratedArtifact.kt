package com.vukan.agentskillcomposer.model

data class GeneratedArtifact(
    val target: GenerationTarget,
    val artifactType: ArtifactType,
    val title: String,
    val content: String,
    val defaultPath: String,
)
