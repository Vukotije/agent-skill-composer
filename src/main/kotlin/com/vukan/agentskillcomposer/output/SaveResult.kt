package com.vukan.agentskillcomposer.output

import java.nio.file.Path

enum class SaveStatus { CREATED, OVERWRITTEN }

sealed class SaveResult {
    data class Saved(val path: Path, val status: SaveStatus) : SaveResult()
    data class Failed(val path: Path, val message: String, val cause: Throwable? = null) : SaveResult()
}
