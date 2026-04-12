package com.vukan.agentskillcomposer.model

enum class ConventionType(val displayName: String) {
    NAMING("Naming Convention"),
    PACKAGE_STRUCTURE("Package Structure"),
    TEST_STYLE("Test Style"),
    DI_STYLE("Dependency Injection"),
    ERROR_HANDLING("Error Handling");

    override fun toString(): String = displayName
}
