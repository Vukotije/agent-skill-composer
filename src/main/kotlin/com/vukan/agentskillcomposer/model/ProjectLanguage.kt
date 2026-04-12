package com.vukan.agentskillcomposer.model

enum class ProjectLanguage(val displayName: String) {
    KOTLIN("Kotlin"),
    JAVA("Java");

    override fun toString(): String = displayName
}
