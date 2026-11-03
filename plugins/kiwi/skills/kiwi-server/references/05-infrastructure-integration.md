# 5. Infrastructure and Integration - Kiwi Server

The previous chapters covered the domain model, the application layer and querying. This chapter is about the
infrastructure underneath: How the Kiwi-Server Framework configures itself in a Spring Boot application, which
configuration properties are available, and how Kiwi integrates with Spring Security, e-mail delivery, file storage and
the audit log.

## Configuration and Auto-Configuration

Kiwi uses [Spring Boot auto-configuration](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html):
Each Kiwi module registers its auto-configurations in its
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` file. Adding a module dependency to
the project is all that is required, an application annotated with `@SpringBootApplication` does not need to
`@Import` any Kiwi configuration classes, nor declare `@EntityScan` or `@EnableJpaRepositories` for Kiwi's own
entities.

The following auto-configurations are provided by the Kiwi modules:

| Module           | Auto-configuration                        | Provides                                                  |
|------------------|-------------------------------------------|-----------------------------------------------------------|
| `kiwi-framework` | `KiwiFrameworkAutoConfiguration`          | Query schemata, generic handlers, reports, user, clock    |
| `kiwi-framework` | `KiwiBusAutoConfiguration`                | `EventBus`, `CommandBus`, `QueryBus`, `CommandQueryBus`   |
| `kiwi-framework` | `KiwiMailAutoConfiguration`               | `MailSenderService`, Mustache template renderer           |
| `kiwi-framework` | `KiwiWebAutoConfiguration`                | Generic and report endpoints, exception handling, CORS    |
| `kiwi-framework` | `KiwiOpenApiAutoConfiguration`            | OpenAPI documentation of the Kiwi endpoints (SpringDoc)   |
| `kiwi-framework` | `KiwiManagementSecurityAutoConfiguration` | HTTP Basic security for the actuator endpoints (opt-in)   |
| `kiwi-mjml`      | `KiwiMjmlAutoConfiguration`               | MJML e-mail template renderer                             |
| `kiwi-storage`   | `KiwiStoragePackagesAutoConfiguration`    | Registers the `FileEntity` package for entity scanning    |
| `kiwi-storage`   | `KiwiStorageAutoConfiguration`            | `FileStorageService`, `FileEntityService`, file endpoint  |
| `kiwi-audit-log` | `KiwiAuditLogPackagesAutoConfiguration`   | Registers the `AuditLogEntry` package for entity scanning |
| `kiwi-audit-log` | `KiwiAuditLogAutoConfiguration`           | Persistent `AuditLoggerService`                           |

The entities and repositories of the Kiwi modules register themselves, independent of how the application configures
its own entity scanning: Spring Boot scans the package of the `@SpringBootApplication` class by default, and if the
application declares its own `@EntityScan` or `@EnableJpaRepositories`, the Kiwi module packages are added as well.

The Kiwi auto-configurations are ordered after the Spring Boot auto-configurations they depend on (Jackson, Hibernate
JPA, transactions, Spring Mail), and the management security is ordered before the Spring Boot web security, so you
don't need to take care of ordering yourself.

> Note: Spring Boot test slices like `@DataJpaTest` and `@WebMvcTest` don't apply all auto-configurations. Such tests
> have to import the required Kiwi auto-configurations using `@ImportAutoConfiguration`, see
> [Chapter 7: "Testing"](./07-testing.md).

### Replacing framework beans

Every Kiwi bean that is meant to be customized is declared with `@ConditionalOnMissingBean`. To replace a bean,
declare a bean of the same type in your application configuration, and the Kiwi autoconfiguration backs off.
Most beans are declared by their interface type, for example:

- `UserDescriptorProvider`, determining the current user
  (see ["Spring Security Integration"](#spring-security-integration))
- `TransactionService` and `ClockProvider` (see ["Transactions and Clock"](#transactions-and-clock))
- `EventBus`, `CommandBus` and `QueryBus`
- `QuerySchemaService`, `QuerySchemaProvider`, `GenericHandlerProvider` and `AnalyticalReportProvider`
- `MailSenderService` and `MjmlCompiler`
- `FileStorageService`, `LocalFileTokenService` and `SignedFileUriProvider`
- `AuditLoggerService` and `AuditableDetailsSerializer`

Some beans are not replaced but _collected_: All `MailTemplateRenderer` beans form the e-mail rendering pipeline, all
`AuthenticationUserDescriptorConverter` beans are asked to convert the Spring Security authentication, and all
`FieldsetValidation` beans validate requested fieldsets. Declaring such a bean adds it to the existing ones.

```kotlin
@Configuration
class ClockConfig {
    // Replaces the ClockProvider of the KiwiFrameworkAutoConfiguration
    @Bean
    fun clockProvider() = ClockProvider { Clock.system(ZoneId.of("Europe/Berlin")) }
}
```

### Disabling features

Whole features can be switched off using configuration properties:

| Property                               | Default | Effect when `false` / `true`                                   |
|----------------------------------------|---------|----------------------------------------------------------------|
| `kiwi.web.enabled`                     | `true`  | `false` disables the entire Kiwi web layer                     |
| `kiwi.web.generic-controller`          | `true`  | `false` disables the generic entity endpoints                  |
| `kiwi.web.report-controller`           | `true`  | `false` disables the analytical report endpoints               |
| `kiwi.web.open-api.enabled`            | `true`  | `false` disables the OpenAPI documentation of Kiwi endpoints   |
| `kiwi.web.management-security.enabled` | `false` | `true` enables the actuator endpoint security                  |
| `kiwi.web.global-cors.enabled`         | `false` | `true` enables the global CORS configuration                   |
| `kiwi.mail.enabled`                    | `true`  | `false` replaces e-mail delivery with a warning in the log     |
| `kiwi.mail.mjml.enabled`               | `true`  | `false` removes the MJML renderer from the e-mail pipeline     |
| `kiwi.audit-log.enabled`               | `true`  | `false` disables audit logging, including the audit-log module |

To exclude an autoconfiguration entirely, use Spring Boot's standard mechanism:

```yaml
spring:
  autoconfigure:
    exclude:
      - de.kiwi.framework.web.KiwiOpenApiAutoConfiguration
```

### Configuration Properties

All Kiwi configuration properties use the `kiwi` prefix. They are bound by the property classes
`KiwiFrameworkProperties` (`kiwi-framework` module), `MjmlMailProperties` (`kiwi-mjml`), `KiwiStorageProperties`
(`kiwi-storage`) and `KiwiAuditLogProperties` (`kiwi-audit-log`). The modules ship Spring Boot configuration metadata,
so your IDE can auto-complete and document the properties in `application.yml`.

Property names follow Spring Boot's relaxed binding, and so do enum values: `data-uri`, `DataUri` and `DATA_URI`
all bind to the same value.

As an example, the demo todo-app (`demo/todo-app/src/main/resources/application.yml`) configures the local file
storage, while all other properties keep their defaults:

```yaml
kiwi:
  storage:
    provider: local
    local:
      parent-directory: "localFiles"
      token:
        secret-key: "... Base64-encoded secret key, see 'Local File Storage Service' ..."
```

The following tables list all properties by prefix.

#### Command-, query- and event-bus

Prefixes `kiwi.command-bus` and `kiwi.query-bus`:

| Property                 | Type    | Default | Description                                                            |
|--------------------------|---------|---------|------------------------------------------------------------------------|
| `enable-caching`         | Boolean | `true`  | Cache the handler lookup per command/query class                       |
| `transactional`          | Boolean | `true`  | Execute commands/queries in a transaction using the TransactionService |
| `register-handler-beans` | Boolean | `true`  | Register all `CommandHandler`/`QueryHandler` beans on the bus          |

Prefix `kiwi.event-bus`:

| Property                    | Type    | Default | Description                                            |
|-----------------------------|---------|---------|--------------------------------------------------------|
| `enable-caching`            | Boolean | `true`  | Cache the subscriber lookup per event class            |
| `max-recursion-depth`       | Int     | `10`    | Maximum depth of events published by event-subscribers |
| `register-subscriber-beans` | Boolean | `true`  | Register all `EventSubscriber` beans on the bus        |

Exceeding `max-recursion-depth` throws an `EventRecursionDepthExceeded` exception, which protects against
event-subscribers triggering each other endlessly.

#### Audit log

Prefix `kiwi.audit-log`:

| Property           | Type        | Default                     | Description                                       |
|--------------------|-------------|-----------------------------|---------------------------------------------------|
| `enabled`          | Boolean     | `true`                      | Audit-log events and exceptions of the buses      |
| `console`          | Boolean     | `false`                     | Additionally log audit-log entries to the console |
| `serializer`       | Enum        | `Jackson`                   | Serializer for event details (`kiwi-audit-log`)   |
| `include-severity` | Set of Enum | `Error`, `Event`, `Unknown` | Severities persisted by `kiwi-audit-log`          |

`serializer` and `include-severity` are only used by the `kiwi-audit-log` module, see ["Audit Log"](#audit-log).

#### Security

Prefix `kiwi.security`:

| Property      | Type   | Default | Description                                                                        |
|---------------|--------|---------|------------------------------------------------------------------------------------|
| `role-prefix` | String | `ROLE_` | Prefix removed from granted authorities to get the user's roles, empty to keep all |

#### Transactions

Prefix `kiwi.transactions`:

| Property          | Type | Default   | Description                                                         |
|-------------------|------|-----------|---------------------------------------------------------------------|
| `read-isolation`  | Enum | `DEFAULT` | Isolation level of read-only transactions of the TransactionService |
| `write-isolation` | Enum | `DEFAULT` | Isolation level of read-write transactions, including `writeNew`    |

The values are those of Spring's `Isolation` enum (`DEFAULT`, `READ_UNCOMMITTED`, `READ_COMMITTED`,
`REPEATABLE_READ`, `SERIALIZABLE`); `DEFAULT` uses the default of the database. An isolation level only applies when
the `TransactionService` starts a new transaction, not when it joins an active one.

#### Clock

Prefix `kiwi.clock`:

| Property | Type   | Default | Description                                                              |
|----------|--------|---------|--------------------------------------------------------------------------|
| `zone`   | String | UTC     | Zone of the application clock, e.g. `Europe/Berlin` or `+02:00`          |
| `fixed`  | String | -       | Fixed local date-time of the clock, e.g. `2025-01-01T12:00:00` (testing) |

#### Fieldset validation

Prefix `kiwi.fieldset-validation`:

| Property    | Type    | Default | Description                                                                |
|-------------|---------|---------|----------------------------------------------------------------------------|
| `no-cycles` | Boolean | `true`  | Reject fieldsets with cyclic relations                                     |
| `max-depth` | Int     | `3`     | Maximum nesting depth of to-many relations in fieldsets, `-1` for no limit |

Both validations apply to fieldsets requested from the generic endpoints, see
[Chapter 2: "Dynamic fieldsets"](./02-domain-schema.md#dynamic-fieldsets). To-one relations don't count towards the
depth, as they are loaded within the same SQL query.

#### Pagination

Prefix `kiwi.pagination`:

| Property                | Type   | Default | Description                                   |
|-------------------------|--------|---------|-----------------------------------------------|
| `max-page-size`         | Int    | `1000`  | Maximum page size a request may ask for       |
| `default-page-size`     | Int    | `50`    | Page size if the request does not specify one |
| `page-number-parameter` | String | `_page` | Name of the page number request parameter     |
| `page-size-parameter`   | String | `_size` | Name of the page size request parameter       |
| `sort-parameter`        | String | `_sort` | Name of the sort request parameter            |

The pagination properties apply to the Kiwi endpoints and to `Pageable` controller parameters in general.

#### Web layer

Prefix `kiwi.web`:

| Property             | Type        | Default                               | Description                                   |
|----------------------|-------------|---------------------------------------|-----------------------------------------------|
| `enabled`            | Boolean     | `true`                                | Enable the Kiwi web layer                     |
| `generic-controller` | Boolean     | `true`                                | Enable the generic entity endpoints           |
| `report-controller`  | Boolean     | `true`                                | Enable the analytical report endpoints        |
| `naming-strategy`    | Set of Enum | `CamelCase`, `SnakeCase`, `Lowercase` | Entity name variants mapped to endpoint paths |

With the default `naming-strategy`, the `TodoList` entity is served at `/TodoList`, `/todo_list` and `/todolist`.
See [Chapter 6: "Generic Entity Endpoints"](./06-web-mvc-endpoints.md#generic-entity-endpoints).

Prefix `kiwi.web.open-api`:

| Property               | Type    | Default             | Description                                            |
|------------------------|---------|---------------------|--------------------------------------------------------|
| `enabled`              | Boolean | `true`              | Document the Kiwi endpoints, if SpringDoc is available |
| `locale`               | Locale  | `en`                | Locale of the generated documentation texts            |
| `query-params-details` | Enum    | `EntityFiltersOnly` | Query parameters documented for generic queries        |

`query-params-details` is one of `NoFilters`, `EntityFiltersOnly`, `EntityAndDefaultColumnFilters` and
`EntityAndAllColumnFilters`, see [Chapter 6: "OpenAPI Documentation"](./06-web-mvc-endpoints.md#openapi-documentation).

Prefix `kiwi.web.global-cors` (see ["Cross-origin requests"](#cross-origin-requests)):

| Property            | Type     | Default                    | Description                                   |
|---------------------|----------|----------------------------|-----------------------------------------------|
| `enabled`           | Boolean  | `false`                    | Enable a CORS mapping for all paths (`/**`)   |
| `allowed-origins`   | Set      | -                          | Allowed origins, at least one is required     |
| `allowed-methods`   | Set      | all methods except `TRACE` | Allowed HTTP methods                          |
| `allowed-headers`   | Set      | -                          | Allowed request headers, all headers if empty |
| `allow-credentials` | Boolean  | `false`                    | Allow credentials like cookies                |
| `max-age`           | Duration | `30m`                      | Cache duration of preflight responses         |

Prefix `kiwi.web.management-security` (see ["Management endpoint security"](#management-endpoint-security)):

| Property                       | Type    | Default                    | Description                                 |
|--------------------------------|---------|----------------------------|---------------------------------------------|
| `enabled`                      | Boolean | `false`                    | Secure the actuator endpoints               |
| `management-username`          | String  | `management`               | HTTP Basic username                         |
| `management-password`          | String  | -                          | HTTP Basic password, required when enabled  |
| `management-password-encoding` | Enum    | `BCrypt`                   | `BCrypt` hash or `Plain` text password      |
| `http-basic-realm-name`        | String  | `Kiwi Management-Security` | HTTP Basic realm name                       |
| `permit-all-endpoints`         | Set     | `health`                   | Endpoint IDs accessible without credentials |

#### E-mail

Prefix `kiwi.mail` (see ["Rendering and Sending E-Mails"](#rendering-and-sending-e-mails)):

| Property              | Type    | Default | Description                                                  |
|-----------------------|---------|---------|--------------------------------------------------------------|
| `enabled`             | Boolean | `true`  | Enable sending e-mails                                       |
| `from-address`        | String  | `""`    | Sender address of all e-mails                                |
| `html-message`        | Boolean | `true`  | Send the rendered template as HTML instead of plain text     |
| `send-to-java-mail`   | Boolean | `true`  | Deliver e-mails using the Spring `JavaMailSender`            |
| `send-to-console`     | Boolean | `false` | Log the complete e-mail messages to the console              |
| `disabled-warning`    | Boolean | `true`  | Log a warning for each e-mail while sending is disabled      |
| `template-charset`    | Charset | `UTF-8` | Charset of template files                                    |
| `template-cache-size` | Int     | `0`     | Number of compiled Mustache templates to cache, `0` for none |

Prefix `kiwi.mail.mjml` (`kiwi-mjml` module):

| Property    | Type             | Default | Description                                            |
|-------------|------------------|---------|--------------------------------------------------------|
| `enabled`   | Boolean          | `true`  | Add the MJML renderer to the e-mail rendering pipeline |
| `compiler`  | Enum             | `JS`    | MJML compiler implementation, currently only `JS`      |
| `pool-size` | Int              | `1`     | Number of worker threads compiling MJML concurrently   |
| `fonts`     | Map of name: URI | empty   | Web fonts passed to MJML's `fonts` option              |

#### File storage

Prefix `kiwi.storage` (`kiwi-storage` module, see ["File Storage"](#file-storage)):

| Property                               | Type    | Default | Description                                          |
|----------------------------------------|---------|---------|------------------------------------------------------|
| `provider`                             | Enum    | -       | `None`, `DataUri`, `S3` or `Local`, see below        |
| `entity.on-delete-remove-from-storage` | Boolean | `true`  | Delete the stored data when a file entity is deleted |

Prefix `kiwi.storage.local`:

| Property             | Type   | Default   | Description                                                        |
|----------------------|--------|-----------|--------------------------------------------------------------------|
| `parent-directory`   | Path   | `storage` | Directory storing the files                                        |
| `token.secret-key`   | String | -         | Base64-encoded key signing file URIs, at least 256 bits, required  |
| `file-server-origin` | URI    | -         | Origin of file URIs, required for uploads outside of HTTP requests |
| `force-scheme`       | String | -         | Scheme of file URIs, e.g. `https` behind a TLS-terminating proxy   |

Prefix `kiwi.storage.s3`:

| Property                      | Type   | Default         | Description                                               |
|-------------------------------|--------|-----------------|-----------------------------------------------------------|
| `region`                      | String | `eu-central-1`  | S3 region                                                 |
| `hostname`                    | String | `amazonaws.com` | S3 service hostname (and port)                            |
| `scheme`                      | String | `https`         | Scheme of the S3 service                                  |
| `bucket`                      | String | `""`            | Bucket storing the files                                  |
| `access-key`                  | String | `""`            | Access key, required                                      |
| `secret-key`                  | String | `""`            | Secret key, required                                      |
| `object-uri-type`             | Enum   | `Subdomain`     | Bucket in the hostname or in the path (`PathPrefix`)      |
| `validate-bucket`             | Enum   | `Validate`      | Bucket check at startup: `None`, `Validate`, `AutoCreate` |
| `anonymize-filename`          | Enum   | `None`          | Object key filename: `None`, `Slugify`, `Anonymous`       |
| `public-access-hostname`      | String | -               | Hostname replacing the S3 hostname in signed URIs         |
| `public-object-mode`          | Enum   | `Acl`           | Public files by object ACL or separate `Bucket`           |
| `public-object-bucket-suffix` | String | `-public`       | Suffix of the public bucket in `Bucket` mode              |

## Transactions and Clock

Two small services of the Kiwi API abstract infrastructure that the domain and application code depend on.

The `TransactionService` executes a function within a transaction, using `read { ... }` for a read-only transaction or
`write { ... }` for a read-write transaction. Kiwi provides an implementation based on Spring's
`PlatformTransactionManager`, joining an already active transaction, with the isolation levels configured by
`kiwi.transactions.*` (see ["Transactions"](#transactions)). The command- and query-bus use it to execute
every command and query in a transaction (queries are read-only by default), see
[Chapter 3: "Command-, Query- and Event-Bus"](./03-application-command-query.md#command--query--and-event-bus). A
command or query class can opt out by overriding its `transactional` property with `false`.

Use the `TransactionService` directly for code that does not run within the buses, e.g. scheduled jobs:

```kotlin
@Component
class TodoStatistics(
    private val transactionService: TransactionService,
    private val todoListRepository: TodoListRepository,
    private val todoItemRepository: TodoItemRepository,
) {
    // Both counts are read within the same read-only transaction
    fun countListsAndItems() = transactionService.read {
        todoListRepository.count() to todoItemRepository.count()
    }
}
```

If no `PlatformTransactionManager` is available, the buses execute commands and queries without transactions.

The `ClockProvider` provides the application-wide `java.time.Clock`. Inject it wherever the current time is needed,
for example `LocalDateTime.now(clockProvider.clock())`, so that tests can control the time. The default clock is the
system clock in UTC. Use `kiwi.clock.zone` to set a different zone offset, and `kiwi.clock.fixed` to freeze the clock
at a given local date-time, e.g. in a test profile. To use a region-based time zone like `Europe/Berlin`, declare your
own `ClockProvider` bean as shown in ["Replacing framework beans"](#replacing-framework-beans).

> Note: The timestamps of `DomainEvent`s and `DomainException`s (`occurredOn`) use the system UTC clock by default,
> unless a `Clock` is passed to their constructor.

## Spring Security Integration

Kiwi does not authenticate users itself. Authentication is entirely the application's responsibility, using
[Spring Security](https://docs.spring.io/spring-security/reference/): form login, HTTP Basic, an OAuth2 resource server
validating JWTs issued by an identity provider, or any other mechanism Spring Security supports. Kiwi reads the
authenticated user from the Spring Security context and represents it as a `UserDescriptor`.

> Note: Kiwi 1.4 included its own JWT token service and bearer token filter (`kiwi.jwt.*`). These were removed in
> Kiwi 1.5. Use Spring Security's OAuth2 resource server support, or your own authentication, instead.

### Securing the todo-app

To enable user authentication, add the `spring-boot-starter-security` dependency to the project `build.gradle.kts`
file:

```kotlin
dependencies {
    // [...] other dependencies
    implementation("org.springframework.boot:spring-boot-starter-security")
}
```

Then declare a `SecurityFilterChain` and a source of user accounts. Like the demo todo-app
(`demo/todo-app/src/main/kotlin/de/kiwi/demo/DemoSecurityConfig.kt`), the following configuration uses HTTP Basic and
form login, with two in-memory users:

```kotlin
package de.kiwi.demo

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class DemoSecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    // (1) Source of user accounts: Two users kept in memory
    @Bean
    fun userDetailsService(passwordEncoder: PasswordEncoder): UserDetailsService {
        return InMemoryUserDetailsManager(
            User.withUsername("user")
                .password(passwordEncoder.encode("user"))
                .roles(Roles.USER) // Granted authority ROLE_USER, role USER
                .build(),

            User.withUsername("admin")
                .password(passwordEncoder.encode("admin"))
                .roles(Roles.USER, Roles.ADMIN)
                .build()
        )
    }

    // (2) All requests require an authenticated user
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            cors { }
            httpBasic { }
            formLogin { permitAll = true }
            logout { permitAll = true }
            csrf { disable() }
            authorizeHttpRequests {
                authorize(anyRequest, authenticated)
            }
        }
        return http.build()
    }
}
```

> Note: Kiwi's role checks compare the **roles** of the user, e.g. `UserDescriptor.isAdmin` checks for the role
> `ADMIN` (`Roles.ADMIN`). The roles are the names of the granted authorities without the prefix `ROLE_`, which Spring
> Security's `User.roles(...)` adds: `User.roles("ADMIN")` grants the authority `ROLE_ADMIN`, i.e. the role `ADMIN`.
> Authorities without the prefix, like `authorities("ADMIN")`, are roles as-is. The prefix is configured by
> `kiwi.security.role-prefix`.

### The current user

The `UserDescriptorProvider` bean returns the `UserDescriptor` of the current request
(see [Chapter 3: "User-Descriptor Interface"](./03-application-command-query.md#user-descriptor-interface)). The
default implementation, `SpringUserDescriptorProvider`, reads the `Authentication` of Spring Security's
`SecurityContextHolder`:

- Without an authentication, or with an anonymous authentication, it returns `UserDescriptor.ANONYMOUS`.
- Otherwise, it asks all `AuthenticationUserDescriptorConverter` beans to convert the authentication, ordered by their
  `order`. The first non-null result is the current user.
- The built-in converter, always asked last, handles authentications whose principal is a Spring Security
  `UserDetails`, returning a `SpringUserDescriptor`.
- If no converter supports the authentication, an exception is thrown.

The `SpringUserDescriptor` maps the `UserDetails` as follows:

| UserDescriptor  | Value                                                                            |
|-----------------|----------------------------------------------------------------------------------|
| `userId`        | `UserIdDetails.userId` if the `UserDetails` implement `UserIdDetails`, else null |
| `identity`      | The `userId` if available, else the username                                     |
| `username`      | `UserDetails.username`                                                           |
| `authenticated` | `UserDetails.isEnabled`                                                          |
| `roles`         | The names of the granted authorities, without the prefix `ROLE_`                 |
| `principal`     | The `UserDetails` instance                                                       |

`UserIdDetails` is a Kiwi interface extending Spring's `UserDetails` with a numeric `userId`. Implement it in your own
`UserDetails` class, or wrap existing `UserDetails` using the `withUserId(userId)` extension function.

In MVC controllers, declare a parameter of type `UserDescriptor` to receive the current user, resolved by the
`UserDescriptorProvider`:

```kotlin
@PatchMapping(path = ["/{itemId}/TodoList/{listId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
fun moveItemToList(
    @PathVariable("itemId") itemId: TodoItemId,
    @PathVariable("listId") listId: TodoListId,
    user: UserDescriptor, // Resolved from the Spring Security context
): ResponseEntity<Unit> {
    val command = MoveTodoItemToList(itemId = itemId, targetListId = listId)
    val result = bus.dispatch(command, user)
    return ResponseEntity.ok(result)
}
```

Outside of controllers, inject the `UserDescriptorProvider` and call `currentUser()`.

### Custom authentication types

If the principal of your authentication is not a `UserDetails`, declare an `AuthenticationUserDescriptorConverter`
bean. A converter returns a `UserDescriptor` for authentications it supports, and `null` for all others, so that the
next converter is asked.

For example, an application using Spring Security's OAuth2 resource server receives a `JwtAuthenticationToken`. A
converter could represent its JWT like this:

```kotlin
import de.kiwi.api.domain.UserDescriptor
import de.kiwi.framework.infrastructure.security.AuthenticationUserDescriptorConverter
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

// (1) UserDescriptor representing the user of a validated JWT
class JwtUserDescriptor(
    override val principal: Jwt,
    override val roles: Set<String>,
) : UserDescriptor {
    override val userId: Long? = null // The subject of the identity provider is not numeric
    override val identity: String = requireNotNull(principal.subject) { "JWT without subject" }
    override val username: String = principal.getClaimAsString("preferred_username") ?: identity
    override val authenticated = true
}

// (2) Converter, registered as bean
@Component
class JwtUserDescriptorConverter : AuthenticationUserDescriptorConverter {
    override fun convert(source: Authentication): UserDescriptor? {
        // Return null for authentications that are not supported by this converter
        val token = source as? JwtAuthenticationToken ?: return null
        val roles = AuthorityUtils.authorityListToSet(token.authorities)
        return JwtUserDescriptor(principal = token.token, roles = roles)
    }
}
```

> Note: By default, Spring Security maps the scopes of a JWT to authorities like `SCOPE_todo`. Configure a
> `JwtAuthenticationConverter` in your security configuration if your roles are contained in a different claim.

To determine the current user without Spring Security at all, replace the `UserDescriptorProvider` bean.

### UserAccount entity

The in-memory users of the demo are sufficient to get started. Most applications store user accounts in the database
instead, so that entities can reference them.

Declare a `UserAccount` entity, which also implements the Kiwi `UserIdDetails` interface. This way, the entity can be
used by Spring Security's authentication directly, and the `UserDescriptor` of an authenticated account provides the
account ID as `userId`:

```kotlin
// Entities are located in `domain` package
package com.example.domain

// Use Kiwi-Server API classes
import de.kiwi.api.domain.*
// Use JPA classes
import jakarta.persistence.*
// Use Spring-Security
import org.springframework.security.core.authority.SimpleGrantedAuthority

// (1) Define a dedicated ID type for UserAccount
@Embeddable // Contains the `id` column attribute
class UserAccountId(value: Long) : EntityId(value)

// (2) Define the UserAccount entity
@Entity
@Table(name = "user_account") // Map to database table
class UserAccount(
    id: UserAccountId,

    // Columns of user_account table
    @Column(name = "username", length = 255, nullable = false, unique = true)
    val accountName: String,

    @Column(name = "password", length = 255, nullable = false)
    private var passwordHash: String,

    @Column(name = "admin", nullable = false)
    var admin: Boolean = false,
) : BaseEntity<UserAccountId>(id), UserIdDetails { // Inherit from BaseEntity, implement UserIdDetails
    // -> Inherits database column: id BIGINT NOT NULL PRIMARY KEY

    // Implement methods required by UserIdDetails
    override val userId get() = id.value
    override fun getUsername() = accountName
    override fun getPassword() = passwordHash
    override fun getAuthorities() = buildSet {
        add(SimpleGrantedAuthority(Roles.USER))
        if (admin) add(SimpleGrantedAuthority(Roles.ADMIN))
    }

    companion object {
        fun create(accountName: String, passwordHash: String) = UserAccount(
            id = EntityId.generate<UserAccountId>(),
            accountName = accountName,
            passwordHash = passwordHash,
        )
    }
}
```

Migrate the new entity to the database using Liquibase:

```yaml
# Extend changelog with new changeSet
databaseChangeLog:
  # [...] previous changeSets
  - changeSet:
      id: add-user-account
      author: your-name-here
      changes:
        - createTable:
            tableName: user_account
            columns:
              - column:
                  name: id
                  type: bigint
                  constraints: { primaryKey: true, nullable: false }
              - column:
                  name: username
                  type: varchar(255)
                  constraints: { nullable: false, unique: true }
              - column:
                  name: password
                  type: varchar(255)
                  constraints: { nullable: false }
              - column:
                  name: admin
                  type: boolean
                  defaultValueBoolean: false
                  constraints: { nullable: false }
```

Finally, declare a repository for the entity, and replace the in-memory `UserDetailsService` of the security
configuration with one loading the accounts from the repository:

```kotlin
// (1) Declare UserAccount repository interface in the application layer
@Repository
interface UserAccountRepository : JpaRepository<UserAccount, UserAccountId> {
    fun findByAccountName(accountName: String): UserAccount?
}

// (2) Load user accounts from the database, in DemoSecurityConfig
@Bean
fun userDetailsService(repository: UserAccountRepository) = UserDetailsService { username ->
    repository.findByAccountName(username) ?: throw UsernameNotFoundException(username)
}
```

Command-handlers can now look up the account of the current user by its `userId`:

```kotlin
val account = user.userId?.let { userAccountRepository.findById(UserAccountId(it)).getOrNull() }
```

### UserAccount AccessControl

With authenticated users in place, the access control rules
of [Chapter 4: "Access Control"](./04-querying-access-control.md#access-control) can be defined for the todo-app.

A todo list stores the username of its author. Users may access the lists they authored, and administrators may
access all lists, as `GenericEntityQuery` allows administrators by default (`accessControlAllowAdmin`):

```kotlin
package com.example.domain.query

import com.example.domain.*
import de.kiwi.api.domain.*
import de.kiwi.api.generic.GenericEntityQuery

// (1) Users can read the todo lists they authored
val readTodoList = AccessControl.where<TodoList> { list, user ->
    equal(list[TodoList_.author], user.username)
}

// (2) Users can read the items of the todo lists they can read
val readTodoItem: AccessControl<TodoItem> = readTodoList.childAccess { item -> item[TodoItem_.todoList] }

// (3) Apply the access control to the generic queries
class TodoListQuery : GenericEntityQuery<TodoList>() {
    override val accessControl = readTodoList

    override fun filterById(id: EntityId) = baseEntityFilter<TodoList>(id)
    override fun preAuthorize(user: UserDescriptor) = user.requireAuthenticated()
}

class TodoItemQuery : GenericEntityQuery<TodoItem>() {
    override val accessControl = readTodoItem

    override fun filterById(id: EntityId) = baseEntityFilter<TodoItem>(id)
    override fun preAuthorize(user: UserDescriptor) = user.requireAuthenticated()
}
```

If you expose user accounts using a generic query, restrict access to them as well, and never include the password
hash in the entity schema. A user may read their own account:

```kotlin
val readUserAccount = AccessControl.where<UserAccount> { account, user ->
    equal(account[UserAccount_.accountName], user.username)
}
```

When entities reference the `UserAccount` entity instead of storing a username, define relation-based rules using an
`AccessControlBuilder`, as described in
[Chapter 4: "Defining Access Control Rules"](./04-querying-access-control.md#defining-access-control-rules).

### Management endpoint security

Applications using [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html) can secure
the actuator endpoints with a dedicated HTTP Basic user, independent of the application's own authentication. This
requires `spring-boot-starter-actuator` and `spring-boot-starter-security`, and is enabled by
`kiwi.web.management-security.enabled`:

```yaml
kiwi:
  web:
    management-security:
      enabled: true
      management-username: "management"
      # BCrypt hash of the password, for example generated by: htpasswd -bnBC 10 "" 'password' | tr -d ':\n'
      management-password: "$2y$10$..."
      # Endpoints accessible without credentials
      permit-all-endpoints: [ health, info ]
```

The `KiwiManagementSecurityAutoConfiguration` then registers a `SecurityFilterChain` for all actuator endpoints, which
takes precedence over the application's filter chains:

- The endpoints listed in `permit-all-endpoints` are accessible without credentials.
- All other endpoints require HTTP Basic authentication with the management user.
- The filter chain is stateless, without CSRF protection, form login or CORS.

The password must be a BCrypt hash (with the default strength of 10 or more), unless
`management-password-encoding` is set to `Plain`. A plain-text password logs a warning at startup and should not be
used in production. A missing or invalid password fails the application startup.

### Cross-origin requests

If browser applications of another origin access the API, configure
[CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS). Kiwi can register a global CORS mapping for all
paths:

```yaml
kiwi:
  web:
    global-cors:
      enabled: true
      allowed-origins: [ "https://todo.example.com" ]
      allow-credentials: true
```

At least one allowed origin is required when enabled. All other properties have defaults, see
["Configuration Properties"](#configuration-properties). The global CORS mapping is part of the Kiwi web layer, so it
is not registered with `kiwi.web.enabled=false`.

When using Spring Security, enable its CORS support in the filter chain using `cors { }` as in the demo's
`DemoSecurityConfig`, so that preflight requests are answered before authentication.

## Rendering and Sending E-Mails

The Kiwi API module declares the `MailSenderService` interface for sending e-mails rendered from templates. The
`kiwi-framework` module implements it using the Spring `JavaMailSender`, and renders templates with
[Mustache](https://mustache.github.io/) and optionally [MJML](https://mjml.io/).

### Sending e-mails

An e-mail consists of the recipients, a subject, the template location and the template properties, and optionally
attachments and additional headers. The `sendEmail` extension function provides a builder for it:

```kotlin
package com.example.application

import com.example.domain.command.TodoListCreated
import de.kiwi.api.application.event.EventSubscriber
import de.kiwi.api.application.service.MailSenderService
import de.kiwi.api.application.service.sendEmail
import de.kiwi.api.domain.DomainResult
import de.kiwi.api.domain.UserDescriptor
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class WhenTodoListCreatedThenNotify(
    private val mailSenderService: MailSenderService,
) : EventSubscriber<TodoListCreated> {
    override fun handle(event: TodoListCreated, user: UserDescriptor) = DomainResult { _ ->
        val list = event.todoList
        mailSenderService.sendEmail(
            // Template location, loaded as Spring resource
            template = "classpath:mail/todoListCreated.mjml",
            // Properties available within the template
            properties = mapOf("title" to list.title, "author" to list.author),
        ) {
            subject = "New todo list: ${list.title}"
            recipients {
                to("team@example.com")
                bcc("archive@example.com")
            }
            attachments {
                inline("logo.png", "image/png", ClassPathResource("mail/logo.png"))
            }
            setHeader("X-Todo-List", list.id.toString())
        }
    }
}
```

Alternatively, call `MailSenderService.sendEmail(recipients, email)` with a list of recipients (created by
`MailSenderService.to(...)`, `cc(...)` and `bcc(...)`) and a `SendEmail` instance.

E-mails are sent synchronously. In the above example, the e-mail is sent within the transaction of the command that
created the todo list.

### Templates and the rendering pipeline

The template location is a Spring resource location, like `classpath:mail/todoListCreated.mjml` or
`file:/etc/example/mail/welcome.mustache`. Templates are read using the charset `kiwi.mail.template-charset`.

The template content is then rendered by a pipeline of all `MailTemplateRenderer` beans, sorted by their `order`. Each
renderer receives the output of the previous one, together with the template properties:

1. The Mustache renderer (order `MailTemplateRenderer.TEMPLATE_ORDER`, `0`) inserts the template properties,
   e.g. `{{title}}`. Values are HTML-escaped, use `{{{title}}}` to insert a value as-is.
2. With the `kiwi-mjml` module, the MJML renderer (order `MailTemplateRenderer.MARKUP_ORDER`, `1000`) compiles the
   rendered MJML markup into HTML.

At startup, the pipeline is logged, e.g. `Using MailTemplateRenderer pipeline: (template) -> mustache -> mjml ->
(message)`. To add a renderer, declare a `MailTemplateRenderer` bean with a fitting order. The result of the pipeline is
the e-mail body, sent as HTML unless `kiwi.mail.html-message` is `false`.

Compiled Mustache templates can be cached using `kiwi.mail.template-cache-size`, which is disabled by default. To use
a customized Mustache compiler, declare a `Mustache.Compiler` bean.

### MJML templates

[MJML](https://mjml.io/) is a markup language for responsive e-mails, which compiles into HTML that renders well in
common e-mail clients. Add the `kiwi-mjml` module to use it:

```kotlin
dependencies {
    // [...] other dependencies
    implementation("de.kiwi-framework:kiwi-mjml:$kiwiVersion")
}
```

The module adds the MJML renderer to the pipeline, so all e-mail templates must be MJML templates then. Mustache
expressions are rendered first, then the markup is compiled:

```xml
<!-- src/main/resources/mail/todoListCreated.mjml -->
<mjml>
    <mj-body>
        <mj-section>
            <mj-column>
                <mj-image src="cid:logo.png" width="120px"/>
                <mj-text>{{author}} created the new todo list "{{title}}".</mj-text>
            </mj-column>
        </mj-section>
    </mj-body>
</mjml>
```

The module bundles the MJML 5 compiler, executed by the GraalVM JavaScript engine, so no Node.js installation is needed
at runtime:

- A pool of worker threads compiles the templates, each worker with its own JavaScript context and MJML compiler
  instance. The pool size is configured by `kiwi.mail.mjml.pool-size` (default `1`). If more e-mails are sent at the
  same time, the requests are queued until a worker is available.
- The workers load the MJML compiler in the background, so the application startup is not delayed. E-mails sent in the
  meantime wait until the first worker is ready.
- `kiwi.mail.mjml.fonts` passes web fonts to MJML's `fonts` option, mapping font names to stylesheet URIs:

```yaml
kiwi:
  mail:
    mjml:
      pool-size: 2
      fonts:
        Roboto: "https://fonts.googleapis.com/css?family=Roboto:300,400,500,700"
```

To disable the MJML renderer while keeping the module on the classpath, set `kiwi.mail.mjml.enabled=false`.

### Delivery and Spring Mail

The e-mails are delivered by the `JavaMailSender` of Spring Boot's mail support, configured using the `spring.mail.*`
properties. The demo todo-app sends e-mails to a local mail server provided by its Docker Compose file:

```yaml
spring:
  mail:
    host: 'localhost'
    port: 1925
    username: ''
    password: ''

kiwi:
  mail:
    from-address: "todo@example.com"
```

The delivery depends on the configuration:

- With `kiwi.mail.send-to-java-mail` (default `true`), e-mails are sent using the `JavaMailSender`.
- With `kiwi.mail.send-to-console`, the complete MIME message is additionally logged. During development, set
  `send-to-java-mail: false` and `send-to-console: true` to log e-mails instead of sending them. This still requires
  `spring.mail.host`, as the `JavaMailSender` creates the messages.
- If no `JavaMailSender` is available, because `spring.mail.host` is not configured, e-mails are not sent, and a
  warning is logged for each e-mail.
- With `kiwi.mail.enabled=false`, e-mails are not sent either, and a warning is logged at startup and for each e-mail,
  unless `kiwi.mail.disabled-warning` is `false`.

## File Storage

The `kiwi-storage` module manages uploaded files: It stores the file data using a `FileStorageService`, and the file
metadata using the `FileEntity` entity. Add the module, and include its Liquibase changelog, which creates the
`kiwi_file` table, in your master changelog:

```kotlin
dependencies {
    // [...] other dependencies
    implementation("de.kiwi-framework:kiwi-storage:$kiwiVersion")
}
```

```yaml
databaseChangeLog:
  - include: { file: "classpath:db/changelog/db.storage-master.xml" }
  # [...] application changelogs
```

### Storage providers

The `FileStorageService` interface of the Kiwi API uploads, downloads and deletes file data, and signs file URIs. The
implementation is chosen by `kiwi.storage.provider`:

| Provider  | Stores files                      | Use case                                    |
|-----------|-----------------------------------|---------------------------------------------|
| `None`    | Not at all, the data is discarded | Tests and applications without file uploads |
| `DataUri` | As `data:` URI in the file entity | Small files, tests                          |
| `S3`      | In an S3 bucket                   | Production                                  |
| `Local`   | In a local directory              | Development and single-server deployments   |

The provider has no default: If the storage module is on the classpath, the application fails to start unless
`kiwi.storage.provider` is configured or the application declares its own `FileStorageService` bean, so an application
never discards its files unnoticed:

```
Invalid value 'null' for configuration property 'kiwi.storage.provider'. Validation failed for the following reason:

The storage provider is required, configure one of: none (discards all file data), data-uri, s3, local; or declare a FileStorageService bean
```

To use a different storage, declare your own `FileStorageService` bean, which replaces the configured provider. The
provider isn't required then. Declare the bean in your application, not in an auto-configuration of its own: Like
`@ConditionalOnMissingBean`, the storage auto-configuration only detects beans registered before it.

### File entities

The `FileEntity` (table `kiwi_file`) stores the metadata of a stored file: Its filename, content type, size, uploading
user and time, and the `storageUri` identifying the data in the storage. The `FileEntityService` creates and deletes
file entities together with their data:

```kotlin
// Command uploading a file, e.g. created from a MultipartFile in a controller
class UploadTodoAttachment(
    val filename: String,
    val contentType: String,
    val content: Resource,
) : Command<FileEntity>()

@Component
class UploadTodoAttachmentHandler(
    private val fileEntityService: FileEntityService,
) : CommandHandler<UploadTodoAttachment, FileEntity> {
    override fun handle(command: UploadTodoAttachment, user: UserDescriptor) = DomainResult { record ->
        user.requireAuthenticated()
        // Upload the data to the storage, then save and return the FileEntity
        record(
            fileEntityService.createFileEntity(
                filename = command.filename,
                contentType = command.contentType,
                fileResource = command.content,
                user = user,
            )
        )
    }
}
```

`createFileEntity` records a `FileCreated` event. Pass `siblingFile` to store the file next to an existing file, e.g.
different sizes of an image, and `publicAccess = true` to make the file accessible without a signed URI.

Reference the `FileEntity` from your own entities using a relation, and add a foreign key to `kiwi_file.id` in your
changelog.

Use `FileEntityService.deleteFileEntity(file)` to delete a file entity. It records a `FileDeleted` event, and the
storage module's event-subscriber deletes the stored data, unless `kiwi.storage.entity.on-delete-remove-from-storage`
is `false`. Deleting a `FileEntity` in other ways, e.g. by a database cascade, keeps the stored data.

### Signed file URIs

File URIs of non-public files can't be accessed directly. Instead, the application hands out _signed_ URIs, which grant
access to a file for a limited duration:

- For S3, signed URIs are S3 pre-signed URLs.
- For the local storage, signed URIs contain a token, see ["Local File Storage Service"](#local-file-storage-service).
- Data URIs contain the data itself, so they are returned unchanged.

Sign a URI using `FileStorageService.signFileUri(fileUri, duration)`, or let the entity schema sign it when entities
are serialized: The `SignedFileUriType` column type formats a file URI as signed URI. The `FileFieldsets` provide a
fieldset with a signed `fileUri` field, and your own schemas can use the column type for URI columns:

```kotlin
// Fieldset of FileEntity with URIs signed for one hour
val signedFileFieldset = FileFieldsets.withSignedFileUri(Duration.ofHours(1))

// Column of your own entity schema, for an entity with a property `imageUri: URI`
object ArticleSchema : EntitySchema<Article>() {
    // [...] other fields
    val imageUri by column(Article::imageUri, SignedFileUriType(Duration.ofHours(1)))
}
```

`SignedFileUriType` uses the `SignedFileUriProvider` registered by the storage auto-configuration, which signs URIs
using the `FileStorageService`.

### Local File Storage Service

With `kiwi.storage.provider=local`, files are stored in the directory `kiwi.storage.local.parent-directory`, and served
by the `LocalFileController` at:

```
GET /file/storage/{uuid}/{filename}?_signedToken={token}
```

The controller returns the file if it has been uploaded with public access, or if `_signedToken` is a valid token for
the file. Otherwise, it responds with `403 Forbidden`, and with `404 Not Found` for a valid token of a missing file.
The controller is only registered if the local provider is configured.

The tokens are generated and verified by the `LocalFileTokenService`. The default `HmacLocalFileTokenService` creates
stateless tokens consisting of the file UUID and the expiry time, signed with HMAC-SHA256. This requires a secret key:

```yaml
kiwi:
  storage:
    provider: local
    local:
      parent-directory: "/var/lib/example/files"
      token:
        # Generate using: head -c 32 /dev/random | base64
        secret-key: "... Base64-encoded secret key of at least 256 bits ..."
```

The application fails to start if the secret key is missing or shorter than 256 bits. All application instances
serving the same storage directory must use the same secret key. To use a different token format, declare your own
`LocalFileTokenService` bean.

The file URIs are absolute, based on the origin of the current HTTP request. Configure
`kiwi.storage.local.file-server-origin` to upload files outside of HTTP requests, e.g. in scheduled jobs, and
`kiwi.storage.local.force-scheme` if the application runs behind a TLS-terminating reverse proxy.

> Note: The file endpoint authorizes requests by the signed token, not by the user. If your Spring Security
> configuration requires authentication for all requests, permit requests to `/file/storage/**`, so that signed URIs
> can be used in `<img>` tags or download links.

### S3 File Storage Service

With `kiwi.storage.provider=s3`, files are stored in an S3 bucket of AWS or an S3-compatible object storage:

```yaml
kiwi:
  storage:
    provider: s3
    s3:
      region: eu-central-1
      bucket: example-files
      access-key: "..."
      secret-key: "..."
```

The object key of a file consists of a random prefix and the filename, which can be slugified or anonymized using
`anonymize-filename`. File URIs reference the bucket in the hostname by default, as the S3 client does for its
requests: `{scheme}://{bucket}.s3.{region}.amazonaws.com/{key}` for AWS (the default `hostname`), and
`{scheme}://{bucket}.{hostname}/{key}` for other S3 providers. For object storages that expect the bucket in the path
(`{scheme}://{hostname}/{bucket}/{key}`), like a local MinIO server, set `object-uri-type` to `PathPrefix`.

At startup, the bucket is validated to exist. Set `validate-bucket` to `AutoCreate` to create a missing bucket, or to
`None` to skip the check.

Public files (`publicAccess = true`) are uploaded with a public-read ACL by default. Set `public-object-mode` to
`Bucket` to store them in a separate public bucket instead, named by the bucket name with the suffix
`public-object-bucket-suffix`. If signed URIs must use a different hostname than the S3 client, e.g. a CDN, set
`public-access-hostname`.

## Audit Log

The audit log records what happened in the application, and who did it. Every `Auditable` object can be audit-logged:
It has an `eventName`, an `occurredOn` timestamp, a `severity` and `details`. `DomainEvent`s and `DomainException`s
are auditable, with the severities `Event` and `Error` (`Unknown` for unexpected exceptions wrapped in a
`DomainException.Unknown`). Exceptions that are not relevant for the audit log, like validation errors, have the
severity `Disabled`.

### What is audit-logged

With `kiwi.audit-log.enabled` (default `true`), the buses pass auditable objects to the `AuditLoggerService`:

- The event-bus logs every published `DomainEvent`, before it is passed to the event-subscribers.
- The command-bus logs the `DomainException`s thrown while handling a command.

See [Chapter 3: "Command-, Query- and Event-Bus"](./03-application-command-query.md#command--query--and-event-bus) for
the complete flow. The `details` of an event determine what is logged, usually a fieldset extract of the subject
entity, as in the `TodoListCreated` event of the todo-app.

The audit log is written by all `AuditLoggerService` beans. Without the `kiwi-audit-log` module, there is none, unless
`kiwi.audit-log.console` is `true`: Then the entries are logged to the console, which is useful during development:

```
AuditLog [2025-01-01T12:00:00 admin] de.kiwi.demo.domain.command.TodoListCreated: {"id":"...","title":"..."}
```

### The audit-log module

The `kiwi-audit-log` module stores the audit log in the database table `kiwi_audit_log`. Add the module, and include
its Liquibase changelog in your master changelog:

```kotlin
dependencies {
    // [...] other dependencies
    implementation("de.kiwi-framework:kiwi-audit-log:$kiwiVersion")
}
```

```yaml
databaseChangeLog:
  - include: { file: "classpath:db/changelog/db.audit-log-master.xml" }
  # [...] application changelogs
```

Each `AuditLogEntry` (JPA entity name `AuditLog`) stores the user ID and username, the event name, the severity, the
timestamp, and the event details as a JSON column (`detailsJson`). The details are serialized by the
`AuditableDetailsSerializer`, by default using the application's Jackson `ObjectMapper`.

Events are stored within the transaction of the command, so they are only persisted if the command succeeds. Errors
are stored in a new, separate transaction after the command transaction has been rolled back.

Only entries of the severities in `kiwi.audit-log.include-severity` are stored, by default `Event`, `Error` and
`Unknown`. For example, to store only events:

```yaml
kiwi:
  audit-log:
    include-severity: [ Event ]
```

To customize the stored entries, declare your own `AuditableDetailsSerializer` bean, or replace the
`AuditLoggerService` bean entirely.

> Note: Setting `kiwi.audit-log.enabled=false` also disables the module, including the `AuditLogEntry` entity. The
> Liquibase changelog still creates the table if it is included.

### Querying the audit log

The module does not expose the audit log by itself, since access to it must be restricted. Instead, it provides base
classes for a generic entity query, an entity filter and an analytical report, which you subclass in your application
to define access and expose them using the generic endpoints:

```kotlin
package com.example.domain.query

import de.kiwi.api.domain.*
import de.kiwi.auditlog.domain.*

// (1) Generic query, served as GET /AuditLog and GET /AuditLog/{id}
class AuditLogQuery : AbstractAuditLogQuery() {
    override fun preAuthorize(user: UserDescriptor) = user.requireAdmin()
}

// (2) Entity filter with the properties after, before and userNameLike
class AuditLogFilter : AbstractAuditLogFilter()

// (3) Analytical report, served as GET /AuditLog/_report
class AuditLogReport : AbstractAuditLogReport() {
    override fun preAuthorize(user: UserDescriptor) = user.requireAdmin()
}
```

The query returns the newest entries first. The `AuditLogSchema` returns the event details as JSON, and the
`AuditLogFieldsets.short` fieldset excludes them. The report provides dimensions by date (`occurredOn`,
`occurredYear`, `occurredMonth`, `occurredYearMonth`), user (`userId`, `userName`), `eventName` and `severity`, and the
metrics `total`, `errors` and `events`. See
[Chapter 4: "Analytical Report Query"](./04-querying-access-control.md#analytical-report-query) and
[Chapter 6: "Generic Entity Endpoints"](./06-web-mvc-endpoints.md#generic-entity-endpoints).

> Note: `AbstractAuditLogReport` does not restrict access by itself. Always override `preAuthorize` in your report
> subclass.

### Migrating the audit log to Kiwi 1.5

Before Kiwi 1.5, the event details were stored as binary data. The changelog `db.audit-log-master.xml` includes the
1.5 migration, which converts the existing details into a JSON column (`json` on MySQL and MariaDB, `jsonb` on
PostgreSQL). The migration supports MySQL, MariaDB and PostgreSQL:

- On MySQL and MariaDB, the migration first checks that all existing details are valid UTF-8 encoded JSON, and halts
  without changes otherwise.
- On PostgreSQL, the migration is transactional and rolled back entirely if any details are invalid.

Details written by Kiwi's Jackson serializer are always valid JSON. MySQL and PostgreSQL normalize the stored JSON, so
the details returned by the audit log may differ in key order and whitespace from the serialized form.

---

Next up, [Chapter 6: "Web MVC and Endpoints"](./06-web-mvc-endpoints.md) is about defining HTTP API endpoints for the
application using MVC Controllers.
