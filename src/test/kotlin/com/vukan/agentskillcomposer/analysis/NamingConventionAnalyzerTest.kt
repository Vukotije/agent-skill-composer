package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NamingConventionAnalyzerTest {

    private val analyzer = NamingConventionAnalyzer()

    private fun vfile(name: String): VirtualFile = LightVirtualFile(name, "")

    @Test
    fun `three or more matching files yields HIGH confidence`() {
        val sources = listOf("UserService.kt", "OrderService.kt", "PaymentService.kt").map(::vfile)

        val conventions = analyzer.analyze(sources, emptyList())

        val service = conventions.firstOrNull { it.summary.contains("Service") }
        assertNotNull(service, "Expected *Service convention")
        assertEquals(ConventionConfidence.HIGH, service!!.confidence)
        assertEquals(ConventionType.NAMING, service.type)
        assertEquals(3, service.evidence.size)
    }

    @Test
    fun `exactly two matching files yields MEDIUM confidence`() {
        val sources = listOf("UserService.kt", "OrderService.kt").map(::vfile)

        val service = analyzer.analyze(sources, emptyList())
            .first { it.summary.contains("Service") }

        assertEquals(ConventionConfidence.MEDIUM, service.confidence)
    }

    @Test
    fun `single match is filtered out`() {
        val sources = listOf("UserService.kt").map(::vfile)

        val conventions = analyzer.analyze(sources, emptyList())

        assertFalse(conventions.any { it.summary.contains("Service") })
    }

    @Test
    fun `evidence capped at five files`() {
        val sources = (1..10).map { vfile("Item${it}Service.kt") }

        val service = analyzer.analyze(sources, emptyList())
            .first { it.summary.contains("Service") }

        assertEquals(5, service.evidence.size)
        assertTrue(service.evidence.all { it.endsWith(".kt") })
    }

    @Test
    fun `test file patterns detected separately from source patterns`() {
        val testFiles = listOf("UserServiceTest.kt", "OrderServiceTest.kt", "PaymentServiceTest.kt").map(::vfile)

        val conventions = analyzer.analyze(emptyList(), testFiles)

        val testConvention = conventions.firstOrNull { it.summary.contains("*Test") }
        assertNotNull(testConvention, "Expected *Test convention")
        assertEquals(ConventionConfidence.HIGH, testConvention!!.confidence)
    }

    @Test
    fun `Repository and Controller suffixes detected`() {
        val sources = listOf(
            "UserRepository.kt", "OrderRepository.kt", "PaymentRepository.kt",
            "UserController.kt", "OrderController.kt", "PaymentController.kt",
        ).map(::vfile)

        val conventions = analyzer.analyze(sources, emptyList())

        assertTrue(conventions.any { it.summary.contains("Repository") })
        assertTrue(conventions.any { it.summary.contains("Controller") })
    }

    @Test
    fun `Kotest Spec suffix detected in test files`() {
        val tests = listOf("UserSpec.kt", "OrderSpec.kt", "PaymentSpec.kt").map(::vfile)

        val conventions = analyzer.analyze(emptyList(), tests)

        val spec = conventions.firstOrNull { it.summary.contains("Kotest") }
        assertNotNull(spec, "Expected Kotest *Spec convention")
    }

    @Test
    fun `Dto and DTO both recognised under same pattern`() {
        val sources = listOf("UserDto.kt", "OrderDTO.kt", "PaymentDto.kt").map(::vfile)

        val dto = analyzer.analyze(sources, emptyList()).firstOrNull { it.summary.contains("Dto") }
        assertNotNull(dto, "Expected DTO convention")
        assertEquals(ConventionConfidence.HIGH, dto!!.confidence)
    }

    @Test
    fun `unmatched filenames produce no conventions`() {
        val sources = listOf("Foo.kt", "Bar.kt", "Baz.kt").map(::vfile)
        val conventions = analyzer.analyze(sources, emptyList())
        assertTrue(conventions.isEmpty())
    }
}
