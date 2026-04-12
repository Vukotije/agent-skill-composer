package com.vukan.agentskillcomposer.analysis

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.Processor
import com.vukan.agentskillcomposer.model.ConventionConfidence
import com.vukan.agentskillcomposer.model.ConventionType
import com.vukan.agentskillcomposer.model.DetectedConvention
import java.util.concurrent.Callable

/**
 * Deep structural analysis using the IDE's resolved type system.
 * Extracts patterns that are impossible to get from filename/regex heuristics:
 * repository supertypes, controller method signatures, entity relationships, test slices.
 */
class CodePatternAnalyzer {

    fun analyze(project: Project): List<DetectedConvention> =
        ReadAction.nonBlocking(Callable {
            val scope = GlobalSearchScope.projectScope(project)
            val facade = JavaPsiFacade.getInstance(project)

            listOfNotNull(
                analyzeRepositoryPatterns(facade, scope),
                analyzeControllerPatterns(facade, scope),
                analyzeEntityPatterns(facade, scope),
                analyzeTestSlicePatterns(facade, scope),
                analyzeErrorHandlingPatterns(facade, scope),
            )
        }).inSmartMode(project).executeSynchronously()

    // ── Repository supertypes ──────────────────────────────────────────────

    private fun analyzeRepositoryPatterns(facade: JavaPsiFacade, scope: GlobalSearchScope): DetectedConvention? {
        val repos = findAnnotatedClasses(facade, scope, REPOSITORY_ANNOTATIONS, MAX_CLASSES)
        if (repos.isEmpty()) return null

        val supertypeEvidence = mutableListOf<String>()

        for (repo in repos) {
            val supers = repo.extendsListTypes + repo.implementsListTypes
            for (superType in supers) {
                val resolved = superType.resolve() ?: continue
                val qualifiedName = resolved.qualifiedName ?: continue
                if (KNOWN_REPOSITORY_SUPERTYPES.any { qualifiedName.contains(it) }) {
                    val typeArgs = superType.parameters.joinToString(", ") { it.presentableText }
                    val signature = if (typeArgs.isNotEmpty()) "${resolved.name}<$typeArgs>" else resolved.name.orEmpty()
                    supertypeEvidence += "${repo.name} extends $signature"
                }
            }
        }

        if (supertypeEvidence.isEmpty()) return null

        // Find the common base type
        val commonBase = supertypeEvidence
            .map { it.substringAfter("extends ").substringBefore("<") }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

        return DetectedConvention(
            type = ConventionType.PERSISTENCE_STYLE,
            summary = "Repositories extend $commonBase",
            confidence = if (supertypeEvidence.size >= 2) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
            evidence = supertypeEvidence.take(MAX_EVIDENCE),
        )
    }

    // ── Controller method patterns ─────────────────────────────────────────

    private fun analyzeControllerPatterns(facade: JavaPsiFacade, scope: GlobalSearchScope): DetectedConvention? {
        val controllers = findAnnotatedClasses(facade, scope, CONTROLLER_ANNOTATIONS, MAX_CLASSES)
        if (controllers.isEmpty()) return null

        val mappingCounts = mutableMapOf<String, Int>()
        val returnTypes = mutableMapOf<String, Int>()

        for (controller in controllers) {
            for (method in controller.methods) {
                for (annotation in method.annotations) {
                    val fqn = annotation.qualifiedName ?: continue
                    val shortName = MAPPING_ANNOTATIONS[fqn] ?: continue
                    mappingCounts[shortName] = (mappingCounts[shortName] ?: 0) + 1
                }

                val returnType = method.returnType?.presentableText ?: continue
                if (returnType != "void" && returnType != "Unit") {
                    val normalized = normalizeReturnType(returnType)
                    returnTypes[normalized] = (returnTypes[normalized] ?: 0) + 1
                }
            }
        }

        if (mappingCounts.isEmpty()) return null

        val evidence = mutableListOf<String>()
        val sortedMappings = mappingCounts.entries.sortedByDescending { it.value }
        evidence += "HTTP methods: ${sortedMappings.joinToString(", ") { "${it.key} (${it.value})" }}"

        val dominantReturn = returnTypes.maxByOrNull { it.value }
        if (dominantReturn != null) {
            evidence += "Primary return type: ${dominantReturn.key} (${dominantReturn.value} methods)"
        }

        return DetectedConvention(
            type = ConventionType.API_STYLE,
            summary = "REST endpoints using ${sortedMappings.first().key}/${sortedMappings.getOrNull(1)?.key ?: "..."}" +
                (dominantReturn?.let { ", returning ${it.key}" } ?: ""),
            confidence = ConventionConfidence.HIGH,
            evidence = evidence,
        )
    }

    // ── Entity structure ───────────────────────────────────────────────────

    private fun analyzeEntityPatterns(facade: JavaPsiFacade, scope: GlobalSearchScope): DetectedConvention? {
        val entities = findAnnotatedClasses(facade, scope, ENTITY_ANNOTATIONS, MAX_CLASSES)
        if (entities.isEmpty()) return null

        val evidence = mutableListOf<String>()
        val relationshipCounts = mutableMapOf<String, Int>()

        for (entity in entities) {
            val entityAnnotations = entity.annotations
                .mapNotNull { it.qualifiedName }
                .filter { it in ENTITY_CLASS_ANNOTATIONS }
                .map { "@${it.substringAfterLast('.')}" }

            if (entityAnnotations.isNotEmpty()) {
                evidence += "${entity.name}: ${entityAnnotations.joinToString(", ")}"
            }

            for (field in entity.fields) {
                for (annotation in field.annotations) {
                    val fqn = annotation.qualifiedName ?: continue
                    val shortName = RELATIONSHIP_ANNOTATIONS[fqn] ?: continue
                    relationshipCounts[shortName] = (relationshipCounts[shortName] ?: 0) + 1
                }
            }

            for (method in entity.methods) {
                for (annotation in method.annotations) {
                    val fqn = annotation.qualifiedName ?: continue
                    val shortName = RELATIONSHIP_ANNOTATIONS[fqn] ?: continue
                    relationshipCounts[shortName] = (relationshipCounts[shortName] ?: 0) + 1
                }
            }
        }

        if (relationshipCounts.isNotEmpty()) {
            evidence += "Relationships: ${relationshipCounts.entries.joinToString(", ") { "${it.key} (${it.value})" }}"
        }

        if (evidence.isEmpty()) return null

        val summary = buildString {
            append("${entities.size} JPA entities")
            if (relationshipCounts.isNotEmpty()) {
                append(" with ${relationshipCounts.keys.joinToString("/")}")
            }
        }

        return DetectedConvention(
            type = ConventionType.PERSISTENCE_STYLE,
            summary = summary,
            confidence = if (entities.size >= 2) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
            evidence = evidence.take(MAX_EVIDENCE),
        )
    }

    // ── Test slice patterns ────────────────────────────────────────────────

    private fun analyzeTestSlicePatterns(facade: JavaPsiFacade, scope: GlobalSearchScope): DetectedConvention? {
        val sliceCounts = mutableMapOf<String, Int>()
        val mockCounts = mutableMapOf<String, Int>()

        for ((fqn, label) in TEST_SLICE_ANNOTATIONS) {
            val count = countAnnotatedClasses(facade, scope, fqn)
            if (count > 0) sliceCounts[label] = count
        }

        for ((fqn, label) in MOCK_ANNOTATIONS) {
            val annotation = facade.findClass(fqn, scope) ?: continue
            var count = 0
            AnnotatedElementsSearch.searchPsiMembers(annotation, scope).forEach(Processor {
                count++
                count < 100
            })
            if (count > 0) mockCounts[label] = count
        }

        if (sliceCounts.isEmpty() && mockCounts.isEmpty()) return null

        val evidence = mutableListOf<String>()
        if (sliceCounts.isNotEmpty()) {
            evidence += "Test slices: ${sliceCounts.entries.joinToString(", ") { "${it.key} (${it.value} classes)" }}"
        }
        if (mockCounts.isNotEmpty()) {
            evidence += "Mocking: ${mockCounts.entries.joinToString(", ") { "${it.key} (${it.value} usages)" }}"
        }

        val primarySlice = sliceCounts.maxByOrNull { it.value }?.key
        val primaryMock = mockCounts.maxByOrNull { it.value }?.key

        val summary = buildString {
            if (primarySlice != null) append("Tests use $primarySlice")
            if (primarySlice != null && primaryMock != null) append(" with ")
            if (primaryMock != null) {
                if (primarySlice == null) append("Tests use ")
                append(primaryMock)
            }
        }

        return DetectedConvention(
            type = ConventionType.TESTING_PATTERN,
            summary = summary,
            confidence = ConventionConfidence.HIGH,
            evidence = evidence,
        )
    }

    // ── Error handling patterns ────────────────────────────────────────────

    private fun analyzeErrorHandlingPatterns(facade: JavaPsiFacade, scope: GlobalSearchScope): DetectedConvention? {
        val evidence = mutableListOf<String>()

        val controllerAdviceCount = countAnnotatedClasses(facade, scope, "org.springframework.web.bind.annotation.ControllerAdvice") +
            countAnnotatedClasses(facade, scope, "org.springframework.web.bind.annotation.RestControllerAdvice")

        if (controllerAdviceCount > 0) {
            evidence += "@ControllerAdvice classes: $controllerAdviceCount"
        }

        val exceptionHandlerAnnotation = facade.findClass("org.springframework.web.bind.annotation.ExceptionHandler", scope)
        if (exceptionHandlerAnnotation != null) {
            var handlerCount = 0
            AnnotatedElementsSearch.searchPsiMembers(exceptionHandlerAnnotation, scope).forEach(Processor {
                handlerCount++
                handlerCount < 50
            })
            if (handlerCount > 0) {
                evidence += "@ExceptionHandler methods: $handlerCount"
            }
        }

        if (evidence.isEmpty()) return null

        return DetectedConvention(
            type = ConventionType.ERROR_HANDLING,
            summary = "Centralized error handling via @ControllerAdvice",
            confidence = if (controllerAdviceCount >= 1) ConventionConfidence.HIGH else ConventionConfidence.MEDIUM,
            evidence = evidence,
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun findAnnotatedClasses(
        facade: JavaPsiFacade,
        scope: GlobalSearchScope,
        annotationFqns: List<String>,
        max: Int,
    ): List<PsiClass> {
        val result = mutableListOf<PsiClass>()
        val seen = mutableSetOf<String>()

        for (fqn in annotationFqns) {
            if (result.size >= max) break
            val annotation = facade.findClass(fqn, scope) ?: continue
            AnnotatedElementsSearch.searchPsiClasses(annotation, scope).forEach(Processor { psiClass ->
                val qn = psiClass.qualifiedName
                if (qn != null && seen.add(qn)) result += psiClass
                result.size < max
            })
        }

        return result
    }

    private fun countAnnotatedClasses(facade: JavaPsiFacade, scope: GlobalSearchScope, annotationFqn: String): Int {
        val annotation = facade.findClass(annotationFqn, scope) ?: return 0
        var count = 0
        AnnotatedElementsSearch.searchPsiClasses(annotation, scope).forEach(Processor {
            count++
            count < 100
        })
        return count
    }

    private fun normalizeReturnType(type: String): String = when {
        type.startsWith("ResponseEntity") -> "ResponseEntity<T>"
        type.startsWith("Mono") -> "Mono<T>"
        type.startsWith("Flux") -> "Flux<T>"
        type == "String" -> "String (view name)"
        type.startsWith("ModelAndView") -> "ModelAndView"
        else -> type
    }

    companion object {
        private const val MAX_CLASSES = 20
        private const val MAX_EVIDENCE = 8

        private val REPOSITORY_ANNOTATIONS = CommonAnnotations.REPOSITORY

        private val CONTROLLER_ANNOTATIONS = CommonAnnotations.CONTROLLER + listOf(
            "io.micronaut.http.annotation.Controller",
        )

        private val ENTITY_ANNOTATIONS = listOf(
            "jakarta.persistence.Entity",
            "javax.persistence.Entity",
        )

        private val KNOWN_REPOSITORY_SUPERTYPES = listOf(
            "JpaRepository",
            "CrudRepository",
            "PagingAndSortingRepository",
            "ReactiveCrudRepository",
            "R2dbcRepository",
            "MongoRepository",
        )

        private val MAPPING_ANNOTATIONS = mapOf(
            "org.springframework.web.bind.annotation.GetMapping" to "@GetMapping",
            "org.springframework.web.bind.annotation.PostMapping" to "@PostMapping",
            "org.springframework.web.bind.annotation.PutMapping" to "@PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping" to "@DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping" to "@PatchMapping",
            "org.springframework.web.bind.annotation.RequestMapping" to "@RequestMapping",
        )

        private val ENTITY_CLASS_ANNOTATIONS = setOf(
            "jakarta.persistence.Entity",
            "javax.persistence.Entity",
            "jakarta.persistence.Table",
            "javax.persistence.Table",
            "jakarta.persistence.MappedSuperclass",
            "javax.persistence.MappedSuperclass",
        )

        private val RELATIONSHIP_ANNOTATIONS = mapOf(
            "jakarta.persistence.OneToMany" to "@OneToMany",
            "javax.persistence.OneToMany" to "@OneToMany",
            "jakarta.persistence.ManyToOne" to "@ManyToOne",
            "javax.persistence.ManyToOne" to "@ManyToOne",
            "jakarta.persistence.ManyToMany" to "@ManyToMany",
            "javax.persistence.ManyToMany" to "@ManyToMany",
            "jakarta.persistence.OneToOne" to "@OneToOne",
            "javax.persistence.OneToOne" to "@OneToOne",
        )

        private val TEST_SLICE_ANNOTATIONS = listOf(
            "org.springframework.boot.test.context.SpringBootTest" to "@SpringBootTest",
            "org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest" to "@WebMvcTest",
            "org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest" to "@WebFluxTest",
            "org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest" to "@DataJpaTest",
            "org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest" to "@DataMongoTest",
            "io.micronaut.test.extensions.junit5.annotation.MicronautTest" to "@MicronautTest",
        )

        private val MOCK_ANNOTATIONS = listOf(
            "org.springframework.boot.test.mock.mockito.MockBean" to "@MockBean",
            "org.mockito.Mock" to "@Mock",
            "org.mockito.InjectMocks" to "@InjectMocks",
            "io.mockk.impl.annotations.MockK" to "@MockK",
            "io.mockk.impl.annotations.InjectMockKs" to "@InjectMockKs",
        )
    }
}
