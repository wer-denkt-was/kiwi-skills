---
name: kiwi-server
description: Guide for building and changing Spring Boot applications with the Kiwi Server Framework (Kotlin, packages de.kiwi.*, artifacts de.kiwi-framework:kiwi-*). Covers entities and EntityIds, EntitySchemas and fieldsets, commands/queries/events with their handlers and buses, generic entity commands and endpoints, access control, configuration properties (kiwi.*), storage, audit log, testing and troubleshooting. Use this skill whenever the project depends on kiwi-api/kiwi-framework, the code imports de.kiwi, or the user asks about Kiwi, generic endpoints, schemas, fieldsets, DomainResult or UserDescriptor, even if they don't name the framework.
---

# Kiwi Server Framework

Kiwi is an opinionated Kotlin framework on top of Spring Boot, Spring Data JPA and Jackson for CQRS-style web
application servers. This skill describes the version **1.5** (Spring Boot 4.1, Kotlin 2.3, Java 21, Hibernate 7,
Jackson 3). The full documentation is in `references/` (one file per chapter); read the relevant chapter before
writing non-trivial Kiwi code, because the framework has strong conventions and many details are enforced at startup.

If the project still uses Kiwi 1.4 (artifact `kiwi-bundle`, `@Import(KiwiFrameworkConfiguration::class)`,
`CreateBody`/`UpdateBody` in handlers, `com.fasterxml.jackson.databind`), the code examples here won't compile there.
Tell the user, and suggest the `/kiwi:kiwi-upgrade` skill instead of mixing versions.

## Which chapter to read

| Topic                                                                                            | Reference                                     |
|--------------------------------------------------------------------------------------------------|-----------------------------------------------|
| Modules, requirements, package architecture, new project setup                                   | `references/01-introduction.md`               |
| Entities, EntityIds, Liquibase migrations, EntitySchema, fieldsets, validation                   | `references/02-domain-schema.md`              |
| Commands, queries, events, handlers, UserDescriptor, buses, generic commands, DomainResult       | `references/03-application-command-query.md`  |
| QuerySchemaService, filters, sorting/paging, analytical reports, access control, field access    | `references/04-querying-access-control.md`    |
| Auto-configuration, all `kiwi.*` properties, transactions, clock, security, mail, storage, audit | `references/05-infrastructure-integration.md` |
| Controllers, exception handling, generic entity/report endpoints, OpenAPI, CORS                  | `references/06-web-mvc-endpoints.md`          |
| Unit/slice/integration tests, command verification, frontend fieldset code generation            | `references/07-testing.md`                    |
| Custom column types/filter operators, dynamic fieldsets, query engine internals, discovery rules | `references/08-advanced.md`                   |
| Startup failures, request errors, test problems, Jackson 3 pitfalls                              | `references/09-troubleshooting.md`            |
| Glossary, Snowflake IDs, diagrams, example project                                               | `references/99-appendix.md`                   |
| Changes since Kiwi 1.4                                                                           | `references/98-migration-1.5.md`              |

The chapters are long. Search them (e.g. `grep -n "^##" references/03-application-command-query.md`) and read the
sections you need instead of whole files. When the documentation and the project's Kiwi sources disagree, the
library sources (the `-sources.jar` of the dependency) are authoritative; mention the discrepancy to the user.

## Architecture rules

Each application module has a base package with four layers. Keep dependencies pointing inwards:

- `domain`: entities, EntityIds, schemas and fieldsets, commands, queries, events. Depends on nothing else.
- `application`: command/query handlers, event subscribers, repository and service interfaces. Depends on `domain`.
- `infrastructure`: configuration and service implementations.
- `web`: controllers and controller advice.

All state changes go through **commands** dispatched on the bus, all reads through **queries**; controllers and
schedulers dispatch them with `CommandQueryBus.dispatch(command, user)` instead of calling handlers or repositories.
This is what gives commands their transaction, authorization, event publishing and audit log.

## Core idioms

```kotlin
// domain: dedicated ID type and entity
@Embeddable
class TodoListId(value: Long) : EntityId(value)

@Entity
@Table(name = "todo_list")
class TodoList(id: TodoListId, @Column(name = "title") var title: String) : BaseEntity<TodoListId>(id)

// domain: schema (fields available to the API) and fieldsets (named JSON representations)
object TodoListSchema : EntitySchema<TodoList>() {
    val id by entityId()
    val title by column<String>(TodoList::title)
    val items by toMany(TodoList::items, TodoItemSchema)
}

object TodoListFieldsets : FieldsetsAvailable<TodoList>(TodoListSchema) {
    override val default by defineFieldset { includeAllColumns() }
    val details by defineFieldset {
        extendsFieldset(default)
        includeToMany(TodoListSchema.items, TodoItemFieldsets.short)
    }
}

// domain: command; application: handler bean returning a DomainResult with recorded events
data class RenameTodoList(val listId: TodoListId, val title: String) : Command<JsonNode>()

@Component
class RenameTodoListHandler(private val lists: TodoListRepository) : CommandHandler<RenameTodoList, JsonNode> {
    override fun handle(command: RenameTodoList, user: UserDescriptor) = DomainResult { record ->
        val list = lists.requireById(command.listId)
        list.title = command.title
        record(TodoListRenamed(list))
        TodoListFieldsets.default.extractJson(list)
    }
}
```

For plain CRUD, prefer **generic commands and queries** over hand-written ones: a `GenericEntityQuery<E>` subclass
gives `GET /Entity` and `GET /Entity/{id}`, and `GenericCreate`/`GenericUpdate`/`GenericDelete` subclasses with
`GenericCreateHandler`/`GenericUpdateHandler`/`GenericDeleteHandler` beans give `POST`, `PATCH` and `DELETE`.
Create/update commands declare their JSON body as properties (`by schema(Schema.column)`, `by property<T>()`,
`by reference(Schema.toOne)`, `by references(...)`, `by subtype(...)`); handlers apply update patches with
`patchFields`, `ifPatched`, `isPatched`. Details: chapter 3, "Generic Commands and Queries".

Vue frontends of a Kiwi server use Kiwi-Vue (`@werdenktwas/kiwi-vue`, see the `kiwi-vue` skill). Their TypeScript
types for fieldsets and command bodies are generated by the server's `FieldsetSourceGeneratorTool` (chapter 7,
"Frontend Code-Generation"): regenerate them after changing schemas, fieldsets or command body properties.

Authorization: commands and queries implement `Authorized` (`preAuthorize(user)`, optionally `postAuthorize`) and
throw domain exceptions when access is denied. Row-level restrictions on queries use `AccessControl` (chapter 4).

For "only the owner or an admin may change it", choose by what the other users may see:

- Everyone may read the entity, only some may modify it: check in the update/delete handler with
  `user.requireAdminOr { username == entity.owner }`, which throws `AccessDenied` (`403`).
- Others may not even see the entity: override `accessControl` of the `GenericUpdateHandler`/`GenericDeleteHandler`
  (the same `AccessControl` as the query). The entity is then not found (`404`); admins pass unless
  `accessControlAllowAdmin` is `false`.

## API at a glance

The documentation examples often import `de.kiwi.api.domain.*`. The packages of the commonly used symbols:

| Package                           | Symbols                                                                                                                                                                                                  |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `de.kiwi.api.domain`              | `BaseEntity`, `EntityId`, `Command`, `Query`, `DomainEvent`, `SubjectDomainEvent`, `DomainResult`, `UserDescriptor`, `Roles`, `Authorized`, `AccessControl`, `DomainException`, `AccessDenied`, `EntityNotFound`, `Validation`, `validateColumn()`, `requireAuthorization`, `requireAdminOr`, `requireRole`, `requireAuthenticated` |
| `de.kiwi.api.schema`              | `EntitySchema`, `FieldsetsAvailable`, `Fieldset`, `JsonDetailsProvider`                                                                                                                                  |
| `de.kiwi.api.generic`             | `GenericEntityQuery`, `GenericCreate`, `GenericUpdate`, `GenericDelete`                                                                                                                                  |
| `de.kiwi.api.application.generic` | `GenericCreateHandler`, `GenericUpdateHandler`, `GenericDeleteHandler`                                                                                                                                   |
| `de.kiwi.api.application.command` | `CommandHandler`                                                                                                                                                                                         |
| `de.kiwi.api.application.query`   | `QueryHandler`                                                                                                                                                                                           |
| `de.kiwi.api.application.event`   | `EventSubscriber`                                                                                                                                                                                        |
| `de.kiwi.api.application.service` | `CommandQueryBus`, `TransactionService`, `UserDescriptorProvider`                                                                                                                                        |
| `de.kiwi.api.util`                | `requireById` (repository extension)                                                                                                                                                                     |

Signatures of the generic handlers (override with `= DomainResult { record -> … }`):

```kotlin
// GenericCreateHandler<GC, E>
fun createEntity(create: GC, user: UserDescriptor): DomainResult<E>               // returns the new entity
fun afterEntityCreated(created: E, create: GC, user: UserDescriptor): DomainResult<*> // optional
// GenericUpdateHandler<GU, E>
fun updateEntity(update: GU, entity: E, user: UserDescriptor): DomainResult<*>
// GenericDeleteHandler<GD, E>
fun beforeDeleteEntity(delete: GD, entity: E, user: UserDescriptor): DomainResult<*>
```

The lambdas of `requireAuthorization { … }` and `requireAdminOr { … }` have the `UserDescriptor` as receiver.
When unsure about a signature, look it up in the Kiwi sources (`-sources.jar` of the dependency) instead of guessing.

## Things that trip people up

- **Startup checks are strict.** At most one schema, fieldsets and filter class per entity, and one generic
  query/create/update/delete (or `@CreateCommand`/...) class per entity; duplicates fail startup. Only public classes
  are discovered. Generic command/query classes need a no-argument constructor.
- **JSON keys of generic body properties are schema field names**, not Kotlin property names. Unknown keys are rejected
  with `400 entity-body-invalid`, listing all field errors.
- **Generic updates are `PATCH`**, and patching is explicit: `patchFields(entity, update::a, ...)` writes only the given
  properties. Read-only columns (primary keys, `val` properties, e.g. `parentEntityId`) can be declared by
  `schema(...)` to use their value, but `patchFields` on them fails with `GenericCommandMisconfigured` (`500`), even
  if the key is absent. So do handler mistakes like reading an absent update property outside `ifPatched`.
- **Roles** are the granted authorities without the prefix `ROLE_` (`kiwi.security.role-prefix`), so
  `User.roles("ADMIN")` makes `UserDescriptor.isAdmin` true. Use `Roles.ADMIN`/`Roles.USER` constants.
- **Jackson 3**: packages `tools.jackson.*` (annotations stay `com.fasterxml.jackson.annotation`). `JsonNode.map {}`
  is a Jackson member function, iterate arrays with `node.values()`.
- **Access-control subqueries** accept `where { … }`, `AccessControl.where` and `PredicateSpecification`s; plain Spring
  Data `Specification`s fail there with an `IllegalStateException`.
- **Audit log**: events are logged within the command transaction, errors in a separate transaction after rollback.
- **Storage**: `kiwi.storage.provider` has no default: with `kiwi-storage` on the classpath, the startup fails unless
  it's set (`none` discards data, `data-uri`, `s3`, `local`) or the application declares its own `FileStorageService`
  bean. `local` requires `kiwi.storage.local.token.secret-key`.
- **JPA metamodel**: schemas and access control often use `Entity_` classes generated by
  `kapt("org.hibernate.orm:hibernate-processor")`; rebuild after changing entities.
- **New entities need a Liquibase changeset**; never edit changesets that were already applied anywhere.
- **Securing the endpoints is the application's job.** Without its own `SecurityFilterChain`, Spring Boot's default
  security applies, including CSRF protection, which rejects `POST`/`PATCH`/`DELETE` requests of API clients with
  `403`. Chapter 5, "Spring Security Integration", shows a filter chain for a stateless API.

## Working style

- Follow the patterns already present in the project (naming of commands/events, fieldset names, test style) before
  the generic examples of the documentation.
- After changes, compile and run the affected tests (`./gradlew test` or the module's test task). Many mistakes only
  show up at application startup, so a Spring context test (`@SpringBootTest`) is the fastest way to catch them.
  If the build can't run, say so, and list what remains unverified (imports, signatures, startup checks).
- When a behavior is surprising, check `references/09-troubleshooting.md` before working around it.
