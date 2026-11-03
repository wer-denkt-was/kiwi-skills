# Migrating to Kiwi 1.5

Kiwi 1.5 moves the framework to Spring Boot 4 and Jackson 3, and contains breaking changes of its own. This chapter
lists the changes an application upgrading from Kiwi 1.4 has to make, ordered from the build setup to runtime behavior.

## Baseline

| Component        | Kiwi 1.4 | Kiwi 1.5 |
|------------------|----------|----------|
| Spring Boot      | 3.5      | 4.1      |
| Spring Framework | 6.2      | 7        |
| Spring Data JPA  | 3        | 4        |
| Hibernate ORM    | 6        | 7        |
| Jackson          | 2        | 3        |
| Kotlin           | 2.2      | 2.3      |
| Java             | 21       | 21       |

Follow the
[Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
for your application code as well. The sections below cover what Kiwi applications typically run into.

## Dependencies

- The framework module is published as `de.kiwi-framework:kiwi-framework` instead of `kiwi-bundle`.
- `kiwi-api` and the libraries in its public API (Spring Security core, Spring Data JPA, Jakarta Persistence, Jackson)
  are transitive dependencies of all modules now; the Spring Boot BOM is published as platform dependency.
- Spring Boot starters were renamed or split: `spring-boot-starter-web` → `spring-boot-starter-webmvc`,
  `spring-boot-starter-json` → `spring-boot-starter-jackson`. Test slices need their own test starters, e.g.
  `spring-boot-starter-data-jpa-test` and `spring-boot-starter-webmvc-test`.
- **Liquibase**: replace `org.liquibase:liquibase-core` by `spring-boot-starter-liquibase`. With the bare library,
  migrations silently stop running, as the Liquibase auto-configuration moved into the starter's module.
- `hibernate-jpamodelgen` → `org.hibernate.orm:hibernate-processor` (`kapt`).
- Testcontainers 2: `org.testcontainers:{mariadb,mysql,minio,junit-jupiter}` → `testcontainers-{mariadb,…}`.

## Application setup

- Kiwi uses Spring Boot autoconfiguration. Remove `@Import(KiwiFrameworkConfiguration::class, …)` of Kiwi
  configurations, and `@EntityScan`/`@EnableJpaRepositories` that only list Kiwi packages, see
  [Chapter 5: "Auto-configuration"](./05-infrastructure-integration.md). The configuration classes are named
  `*AutoConfiguration` now.
- Renamed packages: `de.kiwi.auditLog` → `de.kiwi.auditlog`, `de.kiwi.framework.mjml` → `de.kiwi.mjml`.
- Spring Boot classes moved into technology modules, e.g.
  - `EntityScan` into `org.springframework.boot.persistence.autoconfigure`
  - `DataJpaTest` into `org.springframework.boot.data.jpa.test.autoconfigure`
  - `WebMvcTest` into `org.springframework.boot.webmvc.test.autoconfigure`

## Jackson 3

Kiwi's public API uses Jackson 3 types (`JsonNode`, `ObjectMapper`), e.g. in generic commands and
`Fieldset.extractJson`.

- Packages `com.fasterxml.jackson.{core,databind,module}` → `tools.jackson.*`; annotations stay in
  `com.fasterxml.jackson.annotation`.
- Mappers are immutable: `jacksonMapperBuilder().addModule(…).build()` instead of `registerModule`.
- Renames: `TextNode` → `StringNode`, `asText()` → `asString()`, `fieldNames()` → `propertyNames()`,
  `JsonMappingException` → `DatabindException`.
- **Pitfall**: `JsonNode.map(…)` is a member function in Jackson 3 that transforms the node itself, and takes priority
  over Kotlin's `Iterable.map`. Replace `node.map { … }` over array elements by `node.values().map { … }`.

## API changes

- **Generic commands** declare their JSON body explicitly, using body properties instead of `CreateBody`/`UpdateBody`
  (see [Chapter 3: "Generic Commands and Queries"](./03-application-command-query.md#generic-commands-and-queries)):

  ```kotlin
  class CreateTodoItem : GenericCreate<TodoItem>() {
      override val detailsProvider = TodoItemFieldsets.default // was: entityFieldset
      val list by reference(TodoItemSchema.list) // JSON key "list", a TodoListId
      val title by schema(TodoItemSchema.title)
      val content by schema(TodoItemSchema.content)
      override fun preAuthorize(user: UserDescriptor) = Unit
  }
  ```

  Handlers receive the typed command: `createEntity(create: GC, user)`, `updateEntity(update: GU, entity, user)` and
  `afterEntityCreated(created, create, user)`. Update commands are patches, use `patchFields`, `ifPatched` or
  `isPatched` to apply present properties. `entityDetails(…)` and the `Fieldset.jsonDetails` extension were removed.
  The JSON keys of `schema(…)` and `reference(…)` properties are the schema field names.
- JSpecify nullness of Spring 7 and Spring Data 4 changed signatures: `QueryBuilder.Executor<R : Any>`,
  `PageResult.fromPage<T : Any>`, `QuerySchemaService.findPage<E, N : Any>`, and non-null `CriteriaQuery` parameters
  of `AccessControlSpecification` and `GenericEntityFilter.toPredicate`.
- Plain Spring Data `Specification`s can't be applied within access-control subqueries anymore, and fail with an
  `IllegalStateException`. Use `where { … }`, `AccessControl.where`, or `PredicateSpecification`s instead.
- JWT support was removed (`AuthenticationTokenService`, `JwtTokenService`, `BearerTokenAuthenticationFilter`,
  `kiwi.jwt.*`, the `_accessToken` parameter). Configure Spring Security's resource server instead, see
  [Chapter 5](./05-infrastructure-integration.md).
- `MailTemplateRenderer` is an ordered SPI; the renderer pipeline consists of all renderer beans.
- `TransactionService` has a new method `writeNew` with a default implementation.
- `DetailsProvider.toDetails(value)` takes the user as second parameter: `toDetails(value, user: UserDescriptor?)`;
  if it is `null`, the current user of the global `UserDescriptorProvider` is used. Custom implementations and
  lambdas (`DetailsProvider { … }`) need the additional parameter. `SubjectDomainEvent` has an optional `user`
  constructor parameter, passed to its details provider.

## HTTP endpoints

- Generic updates use `PATCH /<entity>/{id}` instead of `PUT`.
- Request bodies of generic commands reject unknown keys. All body errors are reported together as
  `400 Bad Request` with the code `entity-body-invalid`, listing each field error in `errors`.
- Trailing slashes don't match anymore: `/path/` is not `/path`.
- Column-filter parameter names are matched ignoring case, like sort and fieldset names.
- With `kiwi.web.management-security.enabled=true`, a missing or blank `management-password` fails the startup.

## Configuration properties

| Kiwi 1.4                       | Kiwi 1.5                                                                     |
|--------------------------------|------------------------------------------------------------------------------|
| `kiwi.enable-audit-logging`    | `kiwi.audit-log.enabled`                                                     |
| `kiwi.audit-log-console`       | `kiwi.audit-log.console`                                                     |
| `kiwi.mail.template-renderers` | Removed; disable MJML using `kiwi.mail.mjml.enabled=false`                   |
| `kiwi.jwt.*`                   | Removed                                                                      |
| `kiwi.storage.provider`        | No default (was `s3`), required without own `FileStorageService` bean        |
| -                              | `kiwi.storage.local.token.secret-key`, required for the `local` provider     |
| -                              | `kiwi.security.role-prefix`, default `ROLE_`                                 |
| -                              | `kiwi.transactions.read-isolation`, `write-isolation`, default `DEFAULT`     |
| -                              | `kiwi.web.enabled`, `kiwi.web.open-api.enabled`, `kiwi.mail.mjml.pool-size`  |
| `kiwi.clock.zone`              | Also accepts region IDs like `Europe/Berlin`                                 |

## Behavior changes

- **Roles**: the roles of a `UserDescriptor` are the granted authorities without the prefix `ROLE_`, so
  `User.roles("ADMIN")` makes a user an admin. Set `kiwi.security.role-prefix` to an empty string to keep the
  authorities as-is.
- **Audit log**: errors of failed commands are persisted in a separate transaction after the rollback (they were
  rolled back with the command before). Event details are stored as JSON (`AuditLogEntry.detailsJson`, column
  `event_details`); the Liquibase changelog of the audit-log module migrates existing entries.
- **Local file storage**: file URIs are signed with an HMAC token (`_signedToken`) instead of a JWT, so signed URIs
  issued by 1.4 are invalid.
- **S3**: with `object-uri-type: Subdomain`, object URIs of non-AWS providers are `{bucket}.{hostname}` (the host the
  S3 client sends requests to); URIs in the 1.4 format are still accepted.
- **Startup checks**: several schema, fieldsets, filter or generic command classes for the same entity type fail the
  startup. Non-public classes (e.g., private nested test classes) are not discovered.
- **Paging**: `totalLimit` caps paged queries as well: pages are windows within the first `totalLimit` items.

---

Next up, the [Appendix](./99-appendix.md) contains a glossary, details on Snowflake IDs, architecture- and class
diagrams, and an overview of the example application project.
