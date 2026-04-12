package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.util.concurrent.Callable

/**
 * Detects Kotlin-specific idioms via the Kotlin PSI, not light classes.
 * Data classes, sealed hierarchies, extension functions, coroutines, companion objects.
 * This directly shapes what code style an AI agent should produce.
 */
class KotlinIdiomAnalyzer {

    fun analyze(project: Project, sourceFiles: List<VirtualFile>): List<DetectedConvention> {
        val kotlinFiles = sourceFiles.filter { it.extension == "kt" }
        if (kotlinFiles.isEmpty()) return emptyList()

        val sampled = kotlinFiles.take(MAX_FILES)

        val counts = ReadAction.nonBlocking(Callable {
            countIdioms(project, sampled)
        }).inSmartMode(project).executeSynchronously()

        return buildConventions(counts, sampled.size)
    }

    private fun countIdioms(project: Project, files: List<VirtualFile>): IdiomCounts {
        val counts = IdiomCounts()
        val psiManager = PsiManager.getInstance(project)

        for (file in files) {
            val psiFile = psiManager.findFile(file) as? KtFile ?: continue

            psiFile.accept(object : KtTreeVisitorVoid() {
                override fun visitClass(klass: KtClass) {
                    super.visitClass(klass)
                    counts.totalClasses++
                    if (klass.isData()) counts.dataClasses++
                    if (klass.isSealed()) counts.sealedClasses++
                    if (klass.isValue()) counts.valueClasses++
                    if (klass.companionObjects.isNotEmpty()) counts.companionObjects++
                }

                override fun visitNamedFunction(function: KtNamedFunction) {
                    super.visitNamedFunction(function)
                    counts.totalFunctions++
                    if (function.hasModifier(org.jetbrains.kotlin.lexer.KtTokens.SUSPEND_KEYWORD)) {
                        counts.suspendFunctions++
                    }
                    if (function.receiverTypeReference != null && function.parent is KtFile) {
                        counts.extensionFunctions++
                    }
                }
            })
        }

        return counts
    }

    private fun buildConventions(counts: IdiomCounts, fileCount: Int): List<DetectedConvention> {
        val conventions = mutableListOf<DetectedConvention>()

        // Data class usage
        if (counts.dataClasses >= 2) {
            val pct = if (counts.totalClasses > 0) counts.dataClasses * 100 / counts.totalClasses else 0
            conventions += DetectedConvention(
                type = ConventionType.KOTLIN_IDIOM,
                summary = "Data classes used for models ($pct% of classes)",
                confidence = if (counts.dataClasses >= 5) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
                evidence = listOf("${counts.dataClasses} data classes out of ${counts.totalClasses} total classes in $fileCount sampled files"),
            )
        }

        // Sealed class usage
        if (counts.sealedClasses >= 1) {
            conventions += DetectedConvention(
                type = ConventionType.KOTLIN_IDIOM,
                summary = "Sealed classes/interfaces for type hierarchies",
                confidence = if (counts.sealedClasses >= 3) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
                evidence = listOf("${counts.sealedClasses} sealed classes/interfaces found"),
            )
        }

        // Extension functions
        if (counts.extensionFunctions >= 3) {
            conventions += DetectedConvention(
                type = ConventionType.KOTLIN_IDIOM,
                summary = "Top-level extension functions used",
                confidence = if (counts.extensionFunctions >= 5) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
                evidence = listOf("${counts.extensionFunctions} top-level extension functions found"),
            )
        }

        // Coroutines
        if (counts.suspendFunctions >= 2) {
            val pct = if (counts.totalFunctions > 0) counts.suspendFunctions * 100 / counts.totalFunctions else 0
            conventions += DetectedConvention(
                type = ConventionType.KOTLIN_IDIOM,
                summary = "Kotlin coroutines used ($pct% suspend functions)",
                confidence = if (counts.suspendFunctions >= 5) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
                evidence = listOf("${counts.suspendFunctions} suspend functions out of ${counts.totalFunctions} total functions"),
            )
        }

        // Value classes
        if (counts.valueClasses >= 1) {
            conventions += DetectedConvention(
                type = ConventionType.KOTLIN_IDIOM,
                summary = "Value classes used for type safety",
                confidence = ConventionConfidence.MEDIUM,
                evidence = listOf("${counts.valueClasses} value (inline) classes found"),
            )
        }

        // Companion objects
        if (counts.companionObjects >= 3) {
            conventions += DetectedConvention(
                type = ConventionType.KOTLIN_IDIOM,
                summary = "Companion objects used (factory methods / constants)",
                confidence = if (counts.companionObjects >= 5) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
                evidence = listOf("${counts.companionObjects} classes with companion objects"),
            )
        }

        return conventions
    }

    private class IdiomCounts {
        var totalClasses = 0
        var dataClasses = 0
        var sealedClasses = 0
        var valueClasses = 0
        var companionObjects = 0
        var totalFunctions = 0
        var suspendFunctions = 0
        var extensionFunctions = 0
    }

    companion object {
        private const val MAX_FILES = 100
    }
}
