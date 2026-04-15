package com.vukan.agentskillcomposer.analysis

import com.vukan.agentskillcomposer.model.TestFramework
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildFileAnalyzerTest {

    private val analyzer = BuildFileAnalyzer()

    // -- parseCoordinate --

    @Test
    fun `parses Gradle-prefixed coordinate`() {
        val coord = analyzer.parseCoordinate("Gradle: org.springframework.boot:spring-boot-starter:3.2.0")
        assertNotNull(coord)
        assertEquals("org.springframework.boot", coord!!.group)
        assertEquals("spring-boot-starter", coord.artifact)
        assertEquals("3.2.0", coord.version)
    }

    @Test
    fun `parses Maven-prefixed coordinate`() {
        val coord = analyzer.parseCoordinate("Maven: com.fasterxml.jackson.core:jackson-databind:2.15.3")
        assertNotNull(coord)
        assertEquals("com.fasterxml.jackson.core", coord!!.group)
        assertEquals("jackson-databind", coord.artifact)
        assertEquals("2.15.3", coord.version)
    }

    @Test
    fun `parses bare group artifact version coordinate`() {
        val coord = analyzer.parseCoordinate("io.ktor:ktor-server-core:2.3.7")
        assertNotNull(coord)
        assertEquals("io.ktor", coord!!.group)
        assertEquals("ktor-server-core", coord.artifact)
        assertEquals("2.3.7", coord.version)
    }

    @Test
    fun `parses coordinate with classifier`() {
        val coord = analyzer.parseCoordinate("org.example:lib:1.0:sources")
        assertNotNull(coord)
        assertEquals("org.example", coord!!.group)
        assertEquals("lib", coord.artifact)
        assertEquals("1.0", coord.version)
    }

    @Test
    fun `parses coordinate without version`() {
        val coord = analyzer.parseCoordinate("io.ktor:ktor-server-core")
        assertNotNull(coord)
        assertNull(coord!!.version)
    }

    @Test
    fun `rejects single-token name`() {
        assertNull(analyzer.parseCoordinate("weirdname"))
    }

    @Test
    fun `rejects two-token non-dotted group`() {
        assertNull(analyzer.parseCoordinate("module:dependency"))
    }

    @Test
    fun `preserves raw string for evidence`() {
        val raw = "Gradle: io.ktor:ktor-server-core:2.3.7"
        val coord = analyzer.parseCoordinate(raw)
        assertEquals(raw, coord!!.raw)
    }

    // -- detectFrameworks --

    @Test
    fun `detects Spring Boot from starter library`() {
        val coords = listOf(
            BuildFileAnalyzer.LibCoord("org.springframework.boot", "spring-boot-starter", "3.2.0", "raw"),
        )
        val frameworks = analyzer.detectFrameworks(coords)
        assertTrue(frameworks.any { it.name == "Spring Boot" && it.version == "3.2.0" })
    }

    @Test
    fun `detects Spring Web Ktor and JPA independently`() {
        val coords = listOf(
            BuildFileAnalyzer.LibCoord("org.springframework.boot", "spring-boot-starter-web", "3.2.0", "r1"),
            BuildFileAnalyzer.LibCoord("io.ktor", "ktor-server-core", "2.3.7", "r2"),
            BuildFileAnalyzer.LibCoord("jakarta.persistence", "jakarta.persistence-api", "3.1.0", "r3"),
        )
        val names = analyzer.detectFrameworks(coords).map { it.name }
        assertTrue(names.contains("Spring Web"))
        assertTrue(names.contains("Ktor"))
        assertTrue(names.contains("JPA"))
    }

    @Test
    fun `deduplicates by framework name`() {
        val coords = listOf(
            BuildFileAnalyzer.LibCoord("io.ktor", "ktor-server-core", "2.3.7", "r1"),
            BuildFileAnalyzer.LibCoord("io.ktor", "ktor-server-netty", "2.3.7", "r2"),
        )
        val ktor = analyzer.detectFrameworks(coords).filter { it.name == "Ktor" }
        assertEquals(1, ktor.size)
    }

    @Test
    fun `empty coordinates produce empty framework list`() {
        assertTrue(analyzer.detectFrameworks(emptyList()).isEmpty())
    }

    // -- detectTestFrameworks --

    @Test
    fun `detects JUnit 5 from jupiter coordinate`() {
        val coords = listOf(
            BuildFileAnalyzer.LibCoord("org.junit.jupiter", "junit-jupiter-api", "5.11.0", "raw"),
        )
        assertEquals(listOf(TestFramework.JUNIT5), analyzer.detectTestFrameworks(coords))
    }

    @Test
    fun `detects JUnit 4 from junit-junit coordinate`() {
        val coords = listOf(
            BuildFileAnalyzer.LibCoord("junit", "junit", "4.13.2", "raw"),
        )
        assertEquals(listOf(TestFramework.JUNIT4), analyzer.detectTestFrameworks(coords))
    }

    @Test
    fun `detects TestNG`() {
        val coords = listOf(
            BuildFileAnalyzer.LibCoord("org.testng", "testng", "7.9.0", "raw"),
        )
        assertEquals(listOf(TestFramework.TESTNG), analyzer.detectTestFrameworks(coords))
    }

    @Test
    fun `detects Kotest`() {
        val coords = listOf(
            BuildFileAnalyzer.LibCoord("io.kotest", "kotest-runner-junit5", "5.9.0", "raw"),
        )
        assertEquals(listOf(TestFramework.KOTEST), analyzer.detectTestFrameworks(coords))
    }

    @Test
    fun `multiple test frameworks coexist`() {
        val coords = listOf(
            BuildFileAnalyzer.LibCoord("org.junit.jupiter", "junit-jupiter", "5.11.0", "r1"),
            BuildFileAnalyzer.LibCoord("io.kotest", "kotest-runner-junit5", "5.9.0", "r2"),
        )
        val frameworks = analyzer.detectTestFrameworks(coords)
        assertTrue(frameworks.contains(TestFramework.JUNIT5))
        assertTrue(frameworks.contains(TestFramework.KOTEST))
    }

    @Test
    fun `empty coords yield empty test framework list`() {
        assertTrue(analyzer.detectTestFrameworks(emptyList()).isEmpty())
    }
}
