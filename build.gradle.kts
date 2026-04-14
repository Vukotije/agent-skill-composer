plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.14.0"
}

group = "com.vukan.agentskillcomposer"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        intellijIdea("2026.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:


        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "261.*"
        }

        changeNotes = """
            <h3>1.0.0</h3>
            <ul>
                <li>Analyze Kotlin/Java projects using IDE-native APIs (OrderEnumerator, PSI, AnnotatedElementsSearch, FacetManager, Kotlin AST).</li>
                <li>Detect build system, frameworks, test frameworks, naming conventions, package structure, DI style, API routes, Kotlin idioms, concurrency model, validation, and multi-module layout.</li>
                <li>Generate AI-backed guidance for Junie (<code>.junie/AGENTS.md</code>, <code>.junie/skills/&lt;id&gt;/SKILL.md</code>) and Claude Code (<code>CLAUDE.md</code>, <code>.claude/skills/&lt;id&gt;/SKILL.md</code>, <code>.claude/commands/review-changes.md</code>).</li>
                <li>Supports Anthropic, OpenAI, Gemini, and any OpenAI-compatible provider. API key stored via PasswordSafe; live model discovery.</li>
                <li>Preview artifacts as read-only editor tabs before saving; single Save All button with overwrite confirmation.</li>
            </ul>
        """.trimIndent()
    }
}

tasks {
    runIde {
        jvmArgs("-Xmx1536m")
    }

    test {
        useJUnitPlatform()
        maxHeapSize = "512m"
    }

    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
