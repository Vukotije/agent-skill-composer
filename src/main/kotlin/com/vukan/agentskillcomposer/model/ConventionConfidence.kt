package com.vukan.agentskillcomposer.model

enum class ConventionConfidence(val displayName: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low");

    override fun toString(): String = displayName
}
