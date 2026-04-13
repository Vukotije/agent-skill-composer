package com.vukan.agentskillcomposer.output

data class ArtifactMetadata(
    val fileName: String,
    val relativePath: String,
    val requiresFrontmatter: Boolean,
    val frontmatterTemplate: String? = null,
)
