# 7. Testing and Frontend Code-Generation - Kiwi Server

This chapter shows how to test a Kiwi application on different levels, from plain unit tests of the domain model to
integration tests of the whole application, and how to verify that every command and query has a handler. Finally, it
shows how to generate TypeScript types from the entity schemas and fieldsets for frontend applications.

The examples are taken from the demo todo-app (`demo/todo-app/src/test`) and the access-control tests
(`demo/access-control-tests/src/test`).

## Test Dependencies

The demo todo-app uses the following test dependencies:

```kotlin
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
}
```

- Spring Boot 4 provides test starters per technology: `spring-boot-starter-data-jpa-test` for `@DataJpaTest`, and
  `spring-boot-starter-webmvc-test` for `@WebMvcTest` and MockMvc.
- Testcontainers 2 artifacts are named `testcontainers-<module>`, for example `testcontainers-mysql` or
  `testcontainers-mariadb`. The JDBC driver of the database must be on the test runtime classpath as well.
- Tests with Testcontainers require a running Docker environment.

### Mockito agent

Mockito creates mocks of final classes, which includes all Kotlin classes by default, using an instrumentation agent.
Since JDK 21, loading such an agent dynamically at runtime is deprecated and results in warnings, and future JDKs will
disallow it. Mockito therefore recommends attaching its agent explicitly to the test JVM. In a Gradle build, add a
configuration for the agent and pass it as `-javaagent` argument to the test task:

```kotlin
val mockitoAgent = configurations.create("mockitoAgent")

dependencies {
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

tasks.test {
    useJUnitPlatform()
    // Resolve the agent file when the test task runs, not while configuring the build
    val agentFiles: FileCollection = mockitoAgent
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        agentFiles.files.map { "-javaagent:${it.absolutePath}" }
    })
}
```

With the Spring dependency-management plugin, the version of `mockito-core` is managed by Spring Boot. Without it, add
the Spring Boot BOM to the agent configuration as well: `mockitoAgent(platform(SpringBootPlugin.BOM_COORDINATES))`.
The Kiwi-Server build itself uses the same setup (see `MockitoAgentArgumentProvider` in `build-logic`).

## Unit Tests of the Domain Model

Domain logic implemented in entities and domain functions returning a `DomainResult` (see
[Chapter 3: "DomainResult Convention"](./03-application-command-query.md#domainresult-convention)) can be tested with
plain JUnit tests, without a Spring context or a database:

```kotlin
class TodoItemTest {
    private val alice = dummyUser("Alice Doe")

    @Test
    fun createItem() {
        val list = TodoList.create(title = "Groceries", user = alice).value

        val result = TodoItem.create(list, title = "Milk", content = "2 liters")

        val created = result.findByType<TodoItemCreated>() ?: fail("missing TodoItemCreated")
        assertEquals("Milk", created.todoItem.title)
        assertEquals(TodoItemStatus.Open, created.todoItem.status)
        assertEquals(list.id, created.todoItem.todoList.id)
    }

    @Test
    fun createItemWithBlankTitle() {
        val list = TodoList.create(title = "Groceries", user = alice).value

        assertThrows<DomainException> {
            TodoItem.create(list, title = " ", content = "nothing")
        }
    }
}
```

`dummyUser(...)` is a small test helper of the demo (in `DemoDataJpaTest.kt`), creating an anonymous `UserDescriptor`
implementation with a username and roles.

> Note: Some framework functions use the global `UserDescriptorProvider` if no user is passed explicitly, for example
> extracting a fieldset with field access control rules
> (see [Chapter 4: "Field Access Control"](./04-querying-access-control.md#field-access-control)). Outside a Spring
> context, this global provider is not initialized. Pass the user explicitly, for example
> `TodoItemFieldsets.default.extract(item, user)`.

## Testing Command- and Query-Handlers

Command handlers typically load and persist entities, so they are best tested against a real database. Spring Boot's
`@DataJpaTest` starts a _slice_ of the application context with only the JPA infrastructure: the data source, Hibernate,
Spring Data repositories, Liquibase, and transaction management. Each test method runs in a transaction that is rolled
back afterwards.

### Data-JPA tests with Testcontainers

By default, `@DataJpaTest` replaces the data source with an embedded in-memory database. To test against the same
database as in production, disable the replacement and use a
[Testcontainers JDBC URL](https://java.testcontainers.org/modules/databases/jdbc/). A URL of the form
`jdbc:tc:<database>:<version>:///<database-name>` starts a database container when the first connection is opened.

The demo todo-app declares a meta-annotation for its JPA tests:

```kotlin
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@DataJpaTest(
    showSql = true,
    properties = [
        "spring.test.database.replace=none",
        "spring.datasource.url=jdbc:tc:mysql:latest:///demo-test-db",
        "spring.jpa.hibernate.ddl-auto=validate",
    ]
)
@ContextConfiguration(classes = [DemoDataJpaTestConfig::class])
annotation class DemoDataJpaTest

@SpringBootConfiguration
@EntityScan("de.kiwi.demo.domain")
@ComponentScan("de.kiwi.demo.application", "de.kiwi.demo.infrastructure")
@EnableJpaRepositories("de.kiwi.demo.application")
@Profile("!application-tests") // Disable config for DemoApplicationTests
class DemoDataJpaTestConfig {
    @Bean
    fun objectMapper() = jacksonObjectMapper()

    @Bean
    fun clockProvider() = ClockProvider.SystemUTC
}
```

- The database schema is created by the Liquibase changelog of the application, and Hibernate validates the entity
  mappings against it (`ddl-auto=validate`). This also tests the changelog.
- The configuration scans the entities, repositories and handlers of the application.
- The slice does not contain the Jackson autoconfiguration, so the `ObjectMapper` used to bind generic commands to
  their JSON body is declared explicitly.
- The `@Profile` annotation excludes this configuration from the full application tests (see below), which would
  otherwise pick it up by component scanning.

> Note: Pin the database version in the Testcontainers URL (for example `jdbc:tc:mysql:8.4:///demo-test-db`) to get
> reproducible test runs. The demo uses `latest` for simplicity.

The handlers are then tested by invoking them directly:

```kotlin
@DemoDataJpaTest
class TodoItemCommandHandlersTest {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactions: TransactionTemplate

    @Autowired
    private lateinit var updateTodoItemHandler: UpdateTodoItemHandler

    private val alice = dummyUser("Alice Doe")

    @Test
    fun updateEntity() {
        val list = tx { entityManager.merge(TodoList.create("example list", alice).value) }!!
        val item = tx { entityManager.merge(TodoItem.create(list, "old title", "old content").value) }!!

        val command = UpdateTodoItem().apply {
            entityId = item.id
            bind(objectMapper.readTree("""{ "title": "new title!", "status": "Done" }"""), objectMapper)
        }
        val events = tx { updateTodoItemHandler.handle(command, user = alice) }

        val updatedEvent = events.findByType<EntityUpdated>() ?: fail("missing EntityUpdated")
        val updatedItem = entityManager.find<TodoItem>(updatedEvent.entityId) ?: fail("missing updated TodoItem")
        assertEquals("new title!", updatedItem.title)
        assertEquals(TodoItemStatus.Done, updatedItem.status)
    }

    private fun <T> tx(action: () -> T) = transactions.execute { action() }
}
```

`handle(command, user)` returns the `DomainResult` of the handler, so the test can assert on the result value and the
recorded events. Note that the domain events are not published to event subscribers in this test, since the command is
not submitted to the command bus.

### Kiwi auto-configurations in slice tests

Test slices like `@DataJpaTest` and `@WebMvcTest` disable the regular `@EnableAutoConfiguration` and only apply the
auto-configurations of their slice. The Kiwi auto-configurations are not part of any slice, so the Kiwi beans (buses,
`QuerySchemaService`, access control executor, audit log, ...) are missing in slice tests.

Tests that need these beans, for example to resolve queries with access control using the query bus, import the Kiwi
auto-configurations explicitly with `@ImportAutoConfiguration`. The access-control tests do this:

```kotlin
@MustBeDocumented
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AccessControlTestConfig::class)
annotation class AccessControlTest

@SpringBootConfiguration
@EntityScan("de.kiwi.demo.access")
@ComponentScan("de.kiwi.demo.access")
@EnableJpaRepositories("de.kiwi.demo.access")
@ImportAutoConfiguration(
    KiwiFrameworkAutoConfiguration::class,
    KiwiBusAutoConfiguration::class,
    KiwiMailAutoConfiguration::class,
    KiwiAuditLogPackagesAutoConfiguration::class,
    KiwiAuditLogAutoConfiguration::class,
)
class AccessControlTestConfig {
    @Bean
    fun conversionService() = ConversionServiceFactoryBean()

    @Bean
    fun objectMapper() = jacksonObjectMapper()

    @Bean
    fun mustacheCompiler() = Mustache.compiler()!!

    // ... further test beans
}
```

Import the auto-configurations of all Kiwi modules your tests rely on, for example
`KiwiStoragePackagesAutoConfiguration` and `KiwiStorageAutoConfiguration` for the file storage. The `...Packages...`
auto-configurations register the entities and repositories of the module, also when the test configuration declares
its own `@EntityScan` and `@EnableJpaRepositories`. Beans that are normally provided by Spring Boot auto-configurations
outside the slice must be declared by the test configuration, like the `ObjectMapper`, the `ConversionService` and the
Mustache compiler above. The access-control tests read their data source from `src/test/resources/application.yml`
(`spring.datasource.url: jdbc:tc:mysql:8://access-control-test-db`).

For `@WebMvcTest` slices, `KiwiWebAutoConfiguration` provides the generated endpoints, the exception handling and the
argument resolvers. The Kiwi framework's own endpoint tests (`GenericEntityControllerTest` in the `bundle` module)
combine `@WebMvcTest` with `@AutoConfigureDataJpa` and import `KiwiFrameworkAutoConfiguration`,
`KiwiBusAutoConfiguration` and `KiwiWebAutoConfiguration`.

## Integration Tests

### Full application tests

`@SpringBootTest` starts the complete application context, including all auto-configurations. The demo todo-app uses
it to test that the application starts, to verify commands and queries, and to generate frontend sources:

```kotlin
@SpringBootTest(
    classes = [DemoApplication::class],
    properties = [
        "spring.profiles.active=application-tests",
        "spring.datasource.url=jdbc:tc:mysql:latest:///kiwi-demo",
        "kiwi.storage.local.token.secret-key=000...000==",
    ]
)
@Testcontainers
class DemoApplicationTests {
    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    companion object {
        // SMTP server for the mail integration
        @JvmStatic
        @Container
        val smtpContainer: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("schickling/mailcatcher:latest"))
                .waitingFor(Wait.forListeningPorts(1025))
                .withExposedPorts(1025)

        @JvmStatic
        @DynamicPropertySource
        fun smtpProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.mail.host", smtpContainer::getHost)
            registry.add("spring.mail.port", smtpContainer::getFirstMappedPort)
        }
    }

    @Test
    fun contextLoads() {
    }

    // ... Command- and query-verification, frontend code generation, see below
}
```

- The profile `application-tests` disables the `DemoDataJpaTestConfig` of the JPA tests.
- Required configuration properties without a default, like the storage provider and the secret key of the local
  file storage (see [Chapter 5: "File Storage"](./05-infrastructure-integration.md#file-storage)), must be set for
  the test. The todo-app configures the provider in its `application.yml`.
- `@Testcontainers` and `@Container` start additional containers, and `@DynamicPropertySource` passes their host and
  port to the application.

### Testing the HTTP API

To test endpoints, including the generated generic entity endpoints, add `spring-boot-starter-webmvc-test` and use
MockMvc with the full application context. `@WithMockUser` (from `spring-security-test`) runs a request as an
authenticated user; Kiwi's default `UserDescriptorProvider` converts such a user into a `UserDescriptor`
(see [Chapter 5: "Spring Security Integration"](./05-infrastructure-integration.md#spring-security-integration)).

```kotlin
@SpringBootTest(classes = [DemoApplication::class], properties = [ /* as in DemoApplicationTests */ ])
@AutoConfigureMockMvc
class TodoListEndpointTest {
    @Autowired
    lateinit var mvc: MockMvc

    @Test
    @WithMockUser(username = "alice", roles = ["USER"])
    fun createTodoList() {
        mvc.post("/TodoList") {
            contentType = MediaType.APPLICATION_JSON
            content = """{ "title": "Groceries" }"""
        }.andExpect {
            status { isCreated() }
            jsonPath("\$.title") { value("Groceries") }
            jsonPath("\$.author") { value("alice") }
        }
    }

    @Test
    @WithMockUser(username = "alice", roles = ["USER"])
    fun unknownColumnFilterOperator() {
        mvc.get("/TodoList") {
            queryParam("title:foo", "bar")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("\$.message") { value("unknown-column-filter-operator") }
        }
    }
}
```

`@AutoConfigureMockMvc` is located in the package `org.springframework.boot.webmvc.test.autoconfigure` in Spring Boot 4.
Unlike `@DataJpaTest`, `@SpringBootTest` does not roll back transactions, so clean up the created data or use
separate data per test.

### Testing custom configuration

To test configuration classes, conditional beans and configuration properties without starting the whole application,
use Spring Boot's `ApplicationContextRunner` (or `WebApplicationContextRunner` for web configuration). It starts small
contexts with a given set of auto-configurations and user configurations. The Kiwi modules test their own
auto-configurations this way (`KiwiFrameworkAutoConfigurationTest`, `KiwiStorageAutoConfigurationTest`,
`KiwiAuditLogAutoConfigurationTest`).

The following test checks the custom exception resolvers of
[Chapter 6: "Exception Handling"](./06-web-mvc-endpoints.md#exception-handling) and the effect of a Kiwi property. It
uses an H2 in-memory database (`com.h2database:h2` as test runtime dependency):

```kotlin
class DemoExceptionResolversTest {
    private val contextRunner = WebApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration::class.java,
                HibernateJpaAutoConfiguration::class.java,
                TransactionAutoConfiguration::class.java,
                JacksonAutoConfiguration::class.java,
                HttpMessageConvertersAutoConfiguration::class.java,
                WebMvcAutoConfiguration::class.java,
                KiwiFrameworkAutoConfiguration::class.java,
                KiwiBusAutoConfiguration::class.java,
                KiwiMailAutoConfiguration::class.java,
                KiwiWebAutoConfiguration::class.java,
            )
        )
        .withUserConfiguration(DemoExceptionResolvers::class.java)
        .withPropertyValues("spring.datasource.url=jdbc:h2:mem:exception-resolvers-test")

    @Test
    fun resolvesOptimisticLockingFailure() {
        contextRunner.run { context ->
            assertThat(context).hasNotFailed()
            val manager = context.getBean(ExceptionResponseManager::class.java)
            val response = manager.resolveException(OptimisticLockingFailureException("conflict"))
            assertThat(response.status).isEqualTo(HttpStatus.CONFLICT)
            assertThat(response.body.message).isEqualTo("concurrent-modification")
        }
    }

    @Test
    fun genericEndpointsCanBeDisabled() {
        contextRunner
            .withPropertyValues("kiwi.web.generic-controller=false")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean("genericEntityRouter")
            }
    }
}
```

The Spring Boot 4 auto-configuration classes are located in per-technology packages, for example
`org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`,
`org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration` and
`org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration`.

## Command- and Query-Verification

Each command and query must be handled by exactly one handler. A missing handler is only detected when the command or
query is executed for the first time. `CommandQueryVerification` (package `de.kiwi.framework.support`) checks all
commands and queries of the application at once, so the check can run as part of the test suite:

```kotlin
@SpringBootTest(...)
class DemoApplicationTests {
    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Autowired
    lateinit var resourceLoader: ResourceLoader

    @Test
    fun commandQueryIntegrity() {
        CommandQueryVerification.run(resourceLoader, applicationContext)
    }
}
```

The verification

1. collects the classes of all `CommandHandler` and `QueryHandler` beans of the application context,
2. scans the entity-scan and auto-configuration packages of the application (usually the package of the
   `@SpringBootApplication` class and its subpackages) for concrete, public `Command` and `Query` classes,
3. and checks for each command and query class how many handlers handle it. A handler counts if the command or query
   type it handles is the class itself or a supertype of it, so the shared `GenericEntityQueryHandler` counts for all
   generic entity queries.

Each result is logged: `OKAY` for exactly one handler, `MISSING` if there is no handler, and `TOO MANY HANDLERS` if
there are several. If any command or query is not handled by exactly one handler, the verification throws a
`CommandQueryVerification.VerificationError` (an `AssertionError`) listing the failures, which fails the test.

Without an application context, the packages can be given explicitly. Commands and queries are then scanned in the
domain packages, and the handler classes in the application packages:

```kotlin
@Test
fun commandQueryIntegrity() {
    CommandQueryVerification.run(DefaultResourceLoader()) {
        domain("de.kiwi.demo.domain")
        application("de.kiwi.demo.application")
    }
}
```

In this variant, handler classes are found by classpath scanning, regardless of whether they are registered as beans.

## Frontend Code-Generation

Frontends working with the generic entity endpoints need types for the entity data they receive. The
`FieldsetSourceGeneratorTool` (package `de.kiwi.framework.support`) generates these types from the entity schemas,
fieldsets and commands of the application. The `TypescriptFieldsetSourceRenderer` renders them as a TypeScript module.

The demo todo-app runs the generator as a test of its full application context:

```kotlin
@Test
fun generateFieldsetSources() {
    FieldsetSourceGeneratorTool
        .scanContext(resourceLoader, applicationContext)
        .generateBuildArtifacts(moduleName = "demo", renderer = TypescriptFieldsetSourceRenderer())
}
```

Run the test to (re)generate the sources, for example using Gradle:

```shell
./gradlew :demo:todo-app:test --tests "de.kiwi.demo.DemoApplicationTests.generateFieldsetSources"
```

### Scanning sources

There are two ways to create a `FieldsetSourceGeneratorTool`:

- `scanContext(resourceLoader, applicationContext)` scans the entity-scan, auto-configuration and component-scan
  packages of the application context.
- `scanPackages(resourceLoader, vararg packageName)` scans the given packages. Kiwi's own tests run it within a
  `@DataJpaTest`, so that the JPA metamodel is initialized.

The scan collects `@Entity` classes, `EntitySchema` and `FieldsetsAvailable` objects, generic create and update
command classes, and custom command classes annotated with `@CreateCommand` or `@UpdateCommand`. `EntitySchema` and
`FieldsetsAvailable` classes must be Kotlin `object`s; other classes are skipped with a warning. The interfaces of
generic commands contain their declared body properties: Required properties of create commands are mandatory, all
properties of update commands are optional, and properties accepting `null` have a nullable type.

### Output location

- `generateBuildArtifacts(moduleName, renderer, targetDirectory = "")` writes the file into the build directory of the
  module (for Gradle, `build/`), optionally into the subdirectory `targetDirectory`. The build directory is derived from
  the location of the compiled test classes.
- `generateSources(moduleName, outputDirectory, renderer)` writes the file into the given directory, for example
  directly into the source tree of a frontend project.

Both return the path of the generated file. The TypeScript renderer converts the module name into kebab case and
appends `.model.ts`: the demo writes `demo/todo-app/build/demo.model.ts`, a module name `TodoApp` would result in
`todo-app.model.ts`.

The file header contains the generation timestamp, the generated fieldsets, and the create and update commands (custom
and generic commands alike). With
`withBuildInfo(buildProperties)`, it also contains the artifact coordinates and version of the application, taken from
Spring Boot's `BuildProperties` (available if the build generates `META-INF/build-info.properties`, for example with
`springBoot { buildInfo() }` in Gradle).

### Generated types

The generated module contains:

- An `IdType` helper and a _nominal_ type for each `EntityId` class, for example `TodoItemId`. Nominal ID types are
  strings that cannot be mixed up with each other: a `TodoListId` cannot be assigned to a `TodoItemId`.
- An interface for each entity schema (`ITodoItem`), containing all fields as optional properties: columns, references,
  and subtype references (nullable, as an entity has at most one of its subtypes)
- An interface for each fieldset (`ITodoItemDefault`, `ITodoItemShort`, `ITodoItemOrder`), containing exactly the
  fields of the fieldset; single-column fieldsets become type aliases
- A mapping type for each `FieldsetsAvailable` object (`ITodoItemFieldsets`), mapping fieldset names to their types,
  which can be used to type a frontend API client by the requested `_fieldset`
- An interface for each create and update command (`ICreateTodoItem`, `IUpdateTodoItem`), describing the JSON body of
  `POST` and `PATCH` requests. For generic commands, it contains the declared body properties; required properties of
  create commands are non-optional, all properties of update commands are optional.

Enum columns are rendered as union types of their values, nullable columns as union with `null`, and arrays as
`ReadonlyArray`. For example, the `order` fieldset of the demo results in:

```typescript
export interface ITodoItemOrder {
  readonly id: TodoItemId;
  readonly order: number;
}
```

### Renderer options

The `TypescriptFieldsetSourceRenderer` takes `TypescriptOptions`:

| Option                | Default       | Description                                                          |
|-----------------------|---------------|----------------------------------------------------------------------|
| `indentDepth`         | `2`           | Number of spaces per indentation level                               |
| `readOnlyFields`      | `true`        | Declare fields as `readonly` (generic command fields stay mutable)   |
| `readOnlyArrays`      | `true`        | Use `ReadonlyArray<T>` and readonly tuples instead of mutable arrays |
| `useNominalIdTypes`   | `true`        | Use nominal ID types instead of the plain ID value type              |
| `includeSemicolon`    | `true`        | Terminate statements with semicolons                                 |
| `defaultIdValueType`  | `"string"`    | TypeScript type of ID values                                         |
| `moduleFileExtension` | `".model.ts"` | Extension of the generated file name                                 |
| `endOfLine`           | `"\n"`        | Line separator                                                       |

```kotlin
val renderer = TypescriptFieldsetSourceRenderer(
    TypescriptOptions(indentDepth = 4, includeSemicolon = false)
)
```

Other output languages can be supported by implementing the `FieldsetSourceRenderer` interface.

---

Next up, [Chapter 8: "Advanced Features"](./08-advanced.md) covers custom column types and filter operators, the
dynamic fieldset syntax, and internals of the Kiwi-Server Framework.
