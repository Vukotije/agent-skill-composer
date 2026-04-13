package com.vukan.agentskillcomposer.model

import java.nio.file.Path

object SampleData {

    fun createSampleFacts(projectRoot: Path = Path.of("/sample/project")): ProjectFacts = ProjectFacts(
        projectRoot = projectRoot,
        languages = setOf(ProjectLanguage.KOTLIN, ProjectLanguage.JAVA),
        buildSystem = BuildSystem.GRADLE_KOTLIN,
        frameworks = listOf(
            DetectedFramework("Spring Boot", "3.2.0", "build.gradle.kts: org.springframework.boot"),
            DetectedFramework("Spring Data JPA", null, "build.gradle.kts: spring-boot-starter-data-jpa"),
            DetectedFramework("Spring Web", null, "build.gradle.kts: spring-boot-starter-web"),
        ),
        testFrameworks = listOf(TestFramework.JUNIT5),
        sourceRoots = listOf(Path.of("src/main/kotlin"), Path.of("src/main/java")),
        testRoots = listOf(Path.of("src/test/kotlin")),
        conventions = listOf(
            DetectedConvention(
                type = ConventionType.NAMING,
                summary = "Service classes use *Service suffix",
                confidence = ConventionConfidence.HIGH,
                evidence = listOf("UserService.kt", "OrderService.kt", "PaymentService.kt"),
            ),
            DetectedConvention(
                type = ConventionType.NAMING,
                summary = "Repository interfaces use *Repository suffix",
                confidence = ConventionConfidence.HIGH,
                evidence = listOf("UserRepository.kt", "OrderRepository.kt"),
            ),
            DetectedConvention(
                type = ConventionType.PACKAGE_STRUCTURE,
                summary = "Layer-based package layout: controller, service, repository, model",
                confidence = ConventionConfidence.HIGH,
                evidence = listOf("com.example.app.controller", "com.example.app.service", "com.example.app.repository"),
            ),
            DetectedConvention(
                type = ConventionType.TEST_STYLE,
                summary = "Test classes mirror source structure with *Test suffix",
                confidence = ConventionConfidence.MEDIUM,
                evidence = listOf("UserServiceTest.kt", "OrderServiceTest.kt"),
            ),
            DetectedConvention(
                type = ConventionType.DI_STYLE,
                summary = "Constructor injection via Spring @Service and @Repository",
                confidence = ConventionConfidence.MEDIUM,
                evidence = listOf("@Service annotation on service classes", "@Repository annotation on repository interfaces"),
            ),
        ),
        representativeFiles = listOf(
            RepresentativeFile(
                path = Path.of("src/main/kotlin/com/example/app/service/UserService.kt"),
                role = "Service class",
                snippet = """
                    |@Service
                    |class UserService(
                    |    private val userRepository: UserRepository,
                    |    private val eventPublisher: ApplicationEventPublisher,
                    |) {
                    |    fun findById(id: Long): User =
                    |        userRepository.findById(id)
                    |            .orElseThrow { UserNotFoundException(id) }
                    |
                    |    @Transactional
                    |    fun create(request: CreateUserRequest): User {
                    |        val user = User(name = request.name, email = request.email)
                    |        return userRepository.save(user).also {
                    |            eventPublisher.publishEvent(UserCreatedEvent(it))
                    |        }
                    |    }
                    |}
                """.trimMargin(),
            ),
            RepresentativeFile(
                path = Path.of("src/test/kotlin/com/example/app/service/UserServiceTest.kt"),
                role = "Test class",
                snippet = """
                    |@ExtendWith(MockitoExtension::class)
                    |class UserServiceTest {
                    |    @Mock
                    |    private lateinit var userRepository: UserRepository
                    |
                    |    @InjectMocks
                    |    private lateinit var userService: UserService
                    |
                    |    @Test
                    |    fun `findById returns user when found`() {
                    |        val user = User(id = 1L, name = "Alice", email = "alice@test.com")
                    |        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
                    |
                    |        val result = userService.findById(1L)
                    |
                    |        assertThat(result.name).isEqualTo("Alice")
                    |    }
                    |}
                """.trimMargin(),
            ),
        ),
        suggestedSkills = listOf(
            SkillSuggestion(
                id = "write-tests",
                displayName = "Write Tests",
                description = "Write tests following project conventions",
                reason = "Detected JUnit 5, Mockito, and consistent *Test naming pattern",
            ),
            SkillSuggestion(
                id = "implement-service",
                displayName = "Implement Service",
                description = "Implement services following project patterns",
                reason = "Detected @Service classes with constructor injection and @Transactional usage",
            ),
            SkillSuggestion(
                id = "implement-repository",
                displayName = "Implement Repository",
                description = "Implement Spring Data repositories following project patterns",
                reason = "Detected Spring Data JPA with *Repository interfaces",
            ),
        ),
    )

    fun createSampleArtifact(
        target: GenerationTarget,
        artifactType: ArtifactType,
        defaultPath: String,
    ): GeneratedArtifact {
        val (title, content) = when (target) {
            GenerationTarget.JUNIE -> junieArtifact(artifactType)
            GenerationTarget.CLAUDE -> claudeArtifact(artifactType)
        }
        return GeneratedArtifact(
            target = target,
            artifactType = artifactType,
            title = title,
            content = content,
            defaultPath = defaultPath,
        )
    }

    private fun junieArtifact(type: ArtifactType): Pair<String, String> = when (type) {
        is ArtifactType.ProjectGuidance -> Pair(
            "Junie Project Guidance",
            SAMPLE_JUNIE_AGENTS_MD,
        )
        is ArtifactType.Skill -> Pair(
            "Junie ${type.displayName} Skill",
            sampleSkillContent(type.suggestion),
        )
        is ArtifactType.ReviewChangesCommand ->
            error("ReviewChangesCommand is not applicable to Junie")
    }

    private fun claudeArtifact(type: ArtifactType): Pair<String, String> = when (type) {
        is ArtifactType.ProjectGuidance -> Pair(
            "Claude Project Guidance",
            SAMPLE_CLAUDE_MD,
        )
        is ArtifactType.Skill -> Pair(
            "Claude ${type.displayName} Skill",
            sampleSkillContent(type.suggestion),
        )
        is ArtifactType.ReviewChangesCommand -> Pair(
            "Claude Review Changes Command",
            SAMPLE_REVIEW_CHANGES_COMMAND,
        )
    }

    private fun sampleSkillContent(skill: SkillSuggestion): String = when (skill.id) {
        "write-tests" -> SAMPLE_WRITE_TESTS_SKILL
        "implement-service" -> SAMPLE_IMPLEMENT_SERVICE_SKILL
        "implement-repository" -> SAMPLE_IMPLEMENT_REPOSITORY_SKILL
        else -> """
            |# ${skill.displayName}
            |
            |${skill.description}
            |
            |> Detected because: ${skill.reason}
        """.trimMargin()
    }

    private val SAMPLE_JUNIE_AGENTS_MD = """
        |# Project Guidance
        |
        |## Language & Build
        |This is a Kotlin + Java project using Gradle with Kotlin DSL.
        |
        |## Frameworks
        |- Spring Boot 3.2.0 (Web, Data JPA)
        |- JUnit 5 for testing
        |
        |## Conventions
        |- Service classes use `*Service` suffix with constructor injection
        |- Repository interfaces use `*Repository` suffix
        |- Layer-based package layout: `controller/`, `service/`, `repository/`, `model/`
        |- Tests mirror source structure with `*Test` suffix
        |
        |## Testing Rules
        |- Use `@ExtendWith(MockitoExtension::class)` for unit tests
        |- Use backtick test method names describing behavior
        |- Use AssertJ for assertions
    """.trimMargin()

    private val SAMPLE_CLAUDE_MD = """
        |# Project Instructions
        |
        |## Language & Build
        |Kotlin + Java project using Gradle with Kotlin DSL.
        |
        |## Frameworks
        |- Spring Boot 3.2.0 (Web, Data JPA)
        |- JUnit 5 for testing
        |
        |## Conventions
        |- Service classes: `*Service` suffix, constructor injection via `@Service`
        |- Repositories: `*Repository` suffix, Spring Data interfaces
        |- Package layout: layer-based (`controller`, `service`, `repository`, `model`)
        |- Tests: mirror source path, `*Test` suffix, backtick method names
        |
        |## When writing code
        |- Follow existing constructor injection patterns
        |- Use `@Transactional` for write operations in services
        |- Publish domain events via `ApplicationEventPublisher`
    """.trimMargin()

    private val SAMPLE_WRITE_TESTS_SKILL = """
        |# Write Tests
        |
        |## Test Framework
        |JUnit 5 with Mockito and AssertJ.
        |
        |## Test Structure
        |1. Place test in the same package as the source class under `src/test/kotlin/`
        |2. Name the test class `<SourceClass>Test`
        |3. Use `@ExtendWith(MockitoExtension::class)` for unit tests
        |4. Use backtick method names: `` `should do something when condition` ``
        |
        |## Example
        |```kotlin
        |@ExtendWith(MockitoExtension::class)
        |class UserServiceTest {
        |    @Mock
        |    private lateinit var userRepository: UserRepository
        |
        |    @InjectMocks
        |    private lateinit var userService: UserService
        |
        |    @Test
        |    fun `findById returns user when found`() {
        |        val user = User(id = 1L, name = "Alice", email = "alice@test.com")
        |        whenever(userRepository.findById(1L)).thenReturn(Optional.of(user))
        |
        |        val result = userService.findById(1L)
        |
        |        assertThat(result.name).isEqualTo("Alice")
        |    }
        |}
        |```
    """.trimMargin()

    private val SAMPLE_IMPLEMENT_SERVICE_SKILL = """
        |# Implement Service
        |
        |## Pattern
        |Services in this project follow a consistent pattern:
        |
        |1. Annotate with `@Service`
        |2. Use constructor injection for all dependencies
        |3. Use `@Transactional` for write operations
        |4. Throw domain-specific exceptions (not generic ones)
        |5. Publish events via `ApplicationEventPublisher` for side effects
        |
        |## Example
        |```kotlin
        |@Service
        |class UserService(
        |    private val userRepository: UserRepository,
        |    private val eventPublisher: ApplicationEventPublisher,
        |) {
        |    fun findById(id: Long): User =
        |        userRepository.findById(id)
        |            .orElseThrow { UserNotFoundException(id) }
        |
        |    @Transactional
        |    fun create(request: CreateUserRequest): User {
        |        val user = User(name = request.name, email = request.email)
        |        return userRepository.save(user).also {
        |            eventPublisher.publishEvent(UserCreatedEvent(it))
        |        }
        |    }
        |}
        |```
    """.trimMargin()

    private val SAMPLE_IMPLEMENT_REPOSITORY_SKILL = """
        |# Implement Repository
        |
        |## Pattern
        |Repositories in this project use Spring Data JPA interfaces:
        |
        |1. Create an interface extending `JpaRepository<Entity, ID>`
        |2. Annotate with `@Repository`
        |3. Use Spring Data query derivation for simple queries
        |4. Use `@Query` for complex queries
        |
        |## Example
        |```kotlin
        |@Repository
        |interface UserRepository : JpaRepository<User, Long> {
        |    fun findByEmail(email: String): User?
        |    fun findAllByActiveTrue(): List<User>
        |
        |    @Query("SELECT u FROM User u WHERE u.createdAt > :since")
        |    fun findRecentUsers(@Param("since") since: LocalDateTime): List<User>
        |}
        |```
    """.trimMargin()

    private val SAMPLE_REVIEW_CHANGES_COMMAND = """
        |Review the current git diff and check for:
        |
        |1. **Convention compliance** — Do new classes follow the naming conventions?
        |2. **Test coverage** — Are there tests for new service/controller methods?
        |3. **Injection style** — Is constructor injection used consistently?
        |4. **Transaction boundaries** — Are write operations wrapped in `@Transactional`?
        |5. **Error handling** — Are domain-specific exceptions used instead of generic ones?
        |
        |Report any violations with file paths and suggested fixes.
    """.trimMargin()
}
