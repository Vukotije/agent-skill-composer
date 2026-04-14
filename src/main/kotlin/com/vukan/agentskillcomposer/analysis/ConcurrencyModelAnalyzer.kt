package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.Processor
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.util.concurrent.Callable

class ConcurrencyModelAnalyzer {

    fun analyze(project: Project, sourceFiles: List<VirtualFile>): DetectedConvention? =
        ReadAction.nonBlocking(Callable {
            val scope = GlobalSearchScope.projectScope(project)
            val facade = JavaPsiFacade.getInstance(project)
            val psiManager = PsiManager.getInstance(project)

            val suspendCount = countSuspendFunctions(psiManager, sourceFiles)
            val reactiveCount = countReactiveReturnTypes(facade, scope)
            val blockingServiceMethods = countBlockingServiceMethods(facade, scope)

            classify(suspendCount, reactiveCount, blockingServiceMethods)
        }).inSmartMode(project).executeSynchronously()

    private fun countSuspendFunctions(psiManager: PsiManager, sourceFiles: List<VirtualFile>): Int {
        var count = 0
        val kotlinFiles = sourceFiles.filter { it.extension == "kt" }.take(MAX_FILES)

        for (file in kotlinFiles) {
            val ktFile = psiManager.findFile(file) as? KtFile ?: continue
            ktFile.accept(object : KtTreeVisitorVoid() {
                override fun visitNamedFunction(function: KtNamedFunction) {
                    if (function.hasModifier(KtTokens.SUSPEND_KEYWORD)) count++
                    super.visitNamedFunction(function)
                }
            })
        }

        return count
    }

    private fun countReactiveReturnTypes(facade: JavaPsiFacade, scope: GlobalSearchScope): Int {
        var count = 0
        var controllersScanned = 0

        for (fqn in CONTROLLER_ANNOTATIONS) {
            val annotation = facade.findClass(fqn, scope) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotation, scope).forEach(Processor { controller ->
                controllersScanned++
                for (method in controller.methods) {
                    val returnType = method.returnType?.canonicalText ?: continue
                    if (REACTIVE_TYPES.any { returnType.startsWith(it) }) count++
                }
                controllersScanned < MAX_CONTROLLERS
            })
        }

        return count
    }

    private fun countBlockingServiceMethods(facade: JavaPsiFacade, scope: GlobalSearchScope): Int {
        var count = 0

        for (fqn in SERVICE_ANNOTATIONS) {
            val annotation = facade.findClass(fqn, scope) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotation, scope).forEach(Processor { service ->
                for (method in service.methods) {
                    if (method.modifierList.hasModifierProperty("public")) {
                        val returnType = method.returnType?.canonicalText ?: ""
                        val isReactive = REACTIVE_TYPES.any { returnType.startsWith(it) }
                        if (!isReactive) count++
                    }
                }
                count < 100
            })
        }

        return count
    }

    private fun classify(suspendCount: Int, reactiveCount: Int, blockingCount: Int): DetectedConvention? {
        val total = suspendCount + reactiveCount + blockingCount
        if (total == 0) return null

        val evidence = mutableListOf<String>()
        if (suspendCount > 0) evidence += "$suspendCount suspend functions"
        if (reactiveCount > 0) evidence += "$reactiveCount reactive return types (Mono/Flux/Flow)"
        if (blockingCount > 0) evidence += "$blockingCount blocking service methods"

        val (summary, confidence) = when {
            suspendCount > 0 && reactiveCount == 0 -> {
                "Kotlin coroutines (suspend functions)" to ConventionConfidence.HIGH
            }
            reactiveCount > 0 && suspendCount == 0 -> {
                "Reactive (Mono/Flux)" to ConventionConfidence.HIGH
            }
            suspendCount > 0 && reactiveCount > 0 -> {
                "Mixed coroutines + reactive" to ConventionConfidence.MEDIUM
            }
            blockingCount > 0 -> {
                "Blocking / synchronous" to ConventionConfidence.HIGH
            }
            else -> return null
        }

        return DetectedConvention(
            type = ConventionType.CONCURRENCY_MODEL,
            summary = summary,
            confidence = confidence,
            evidence = evidence,
        )
    }

    companion object {
        private const val MAX_FILES = 80
        private const val MAX_CONTROLLERS = 30

        private val REACTIVE_TYPES = listOf(
            "reactor.core.publisher.Mono",
            "reactor.core.publisher.Flux",
            "kotlinx.coroutines.flow.Flow",
            "java.util.concurrent.CompletableFuture",
            "io.reactivex.rxjava3.core.Single",
            "io.reactivex.rxjava3.core.Observable",
        )

        private val CONTROLLER_ANNOTATIONS = CommonAnnotations.CONTROLLER

        private val SERVICE_ANNOTATIONS = CommonAnnotations.SERVICE
    }
}
