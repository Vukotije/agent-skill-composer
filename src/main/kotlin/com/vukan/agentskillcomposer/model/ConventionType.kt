package com.vukan.agentskillcomposer.model

enum class ConventionType(val displayName: String) {
    NAMING("Naming Convention"),
    PACKAGE_STRUCTURE("Package Structure"),
    TEST_STYLE("Test Style"),
    DI_STYLE("Dependency Injection"),
    ERROR_HANDLING("Error Handling"),
    API_STYLE("API Style"),
    PERSISTENCE_STYLE("Persistence Style"),
    TESTING_PATTERN("Testing Pattern"),
    BUILD_TOOLING("Build & Run"),
    KOTLIN_IDIOM("Kotlin Idiom"),
    API_ROUTES("API Routes"),
    CONCURRENCY_MODEL("Concurrency Model"),
    VALIDATION("Validation"),
    MODULE_STRUCTURE("Module Structure");

    override fun toString(): String = displayName
}
