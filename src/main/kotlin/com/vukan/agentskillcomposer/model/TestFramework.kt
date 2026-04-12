package com.vukan.agentskillcomposer.model

enum class TestFramework(val displayName: String) {
    JUNIT5("JUnit 5"),
    JUNIT4("JUnit 4"),
    TESTNG("TestNG"),
    KOTEST("Kotest");

    override fun toString(): String = displayName
}
