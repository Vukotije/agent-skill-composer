package com.vukan.agentskillcomposer.model

enum class BuildSystem(val displayName: String, val buildFileName: String) {
    GRADLE_KOTLIN("Gradle (Kotlin DSL)", "build.gradle.kts"),
    GRADLE_GROOVY("Gradle (Groovy DSL)", "build.gradle"),
    MAVEN("Maven", "pom.xml");

    override fun toString(): String = displayName
}
