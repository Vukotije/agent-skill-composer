package com.vukan.agentskillcomposer.output

import java.nio.file.Path

interface ArtifactWriter {
    fun save(projectRoot: Path, metadata: ArtifactMetadata, content: String): SaveResult
}
