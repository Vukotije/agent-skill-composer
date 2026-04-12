package com.vukan.agentskillcomposer.model

enum class GenerationTarget(val displayName: String) {
    JUNIE("Junie"),
    CLAUDE("Claude Code");

    override fun toString(): String = displayName
}
