package com.vukan.agentskillcomposer.model

import java.nio.file.Path

data class RepresentativeFile(
    val path: Path,
    val role: String,
    val snippet: String,
) {
    val fileName: String get() = path.fileName.toString()
}
