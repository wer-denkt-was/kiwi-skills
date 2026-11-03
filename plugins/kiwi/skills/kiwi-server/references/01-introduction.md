# 1. Introduction - Kiwi Server

The Kiwi-Server Framework provides a foundation for web-server application development based on
the [Spring Framework](https://spring.io/projects/spring-framework),
[Spring Boot](https://spring.io/projects/spring-boot), and the [Kotlin Programming Language](https://kotlinlang.org/).

This chapter explains the basic Kiwi Server concepts, and how to set up a new project.

```
 _    _          _
| |  (_)        (_)
| | _____      ___     __ _
| |/ / \ \ /\ / / |   /  ('>-
|   <| |\ V  V /| |   \__/
|_|\_\_| \_/\_/ |_|    L\_
```

## What you need to know

Prerequisites required to use this framework to its full extent include:

- Programming in Kotlin, see ["Get started with Kotlin"](https://kotlinlang.org/docs/getting-started.html)
- Using the Spring Framework and its associated Projects
    - [Spring Core](https://docs.spring.io/spring-framework/reference/core.html)
    - [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa.html)
    - [Spring Web Servlet](https://docs.spring.io/spring-framework/reference/web.html)
    - [Spring Security](https://docs.spring.io/spring-security/reference/index.html)
    - [Spring Boot](https://docs.spring.io/spring-boot/index.html)
- Familiarity with underlying Java Libraries and Jakarta APIs
    - Database communication using
      [JDBC](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/module-summary.html),
      and entity persistence using [Jakarta Persistence (JPA)](https://jakarta.ee/specifications/persistence/3.2/)
    - JPA Queries with
      the [Criteria API](https://jakarta.ee/learn/docs/jakartaee-tutorial/current/persist/persistence-criteria/persistence-criteria.html)
    - [Hibernate ORM](https://hibernate.org/orm/) as JPA provider
    - [Jackson](https://github.com/FasterXML/jackson) for JSON serialization
    - [Liquibase](https://www.liquibase.com/) for database migration
- For further tutorials and lookup, [baeldung.com](https://www.baeldung.com/start-here) is a recommended resource
    - [Learn Spring](https://www.baeldung.com/spring-tutorial)
    - [Learn Spring Data](https://www.baeldung.com/persistence-with-spring-series)
    - [Learn Spring Security](https://www.baeldung.com/security-spring)
    - [Learn JPA Hibernate](https://www.baeldung.com/learn-jpa-hibernate)
    - [Learn JPA Criteria Queries](https://www.baeldung.com/hibernate-criteria-queries)
    - [Learn Liquibase](https://www.baeldung.com/liquibase-refactor-schema-of-java-app)

A diagram visualising the technology stack for Kiwi-Server can be found
in [Appendix: "Kiwi-Server Framework Stack"](./99-appendix.md#kiwi-server-framework-stack).

## Requirements

Kiwi-Server 1.5 is built on the following baseline. Your application should use the same major versions:

| Component              | Version                                                           |
|------------------------|-------------------------------------------------------------------|
| Java                   | 21 or above                                                       |
| Kotlin                 | 2.3                                                               |
| Spring Boot            | 4.1 (Spring Framework 7, Spring Security 7, Spring Data 2026.0)   |
| Hibernate ORM          | 7 (Jakarta Persistence 3.2), provided by Spring Boot              |
| Jackson                | 3 (packages `tools.jackson.*`), provided by Spring Boot           |
| Liquibase              | Provided by `spring-boot-starter-liquibase`                       |
| Database               | MySQL, MariaDB or PostgreSQL (supported by the Kiwi changelogs)   |
| Build tool (suggested) | Gradle with Kotlin DSL, using KAPT for annotation processing      |

All Kiwi artifacts publish the Spring Boot BOM as a platform dependency, so the versions of Spring and its managed
libraries are aligned with the Spring Boot version Kiwi was built with.

> Note: Jackson 3 moved its packages from `com.fasterxml.jackson.*` to `tools.jackson.*`. Only the annotations, like
> `@JsonProperty` or `@JsonIgnore`, remain in the `com.fasterxml.jackson.annotation` package.

## Kiwi-Server Framework Modules

The Kiwi-Server Framework is composed of modules, which can be included in a project as Maven artifact
dependencies. The group ID for these artifacts is `de.kiwi-framework`.

Every module except the API module contains Spring Boot auto-configurations. Adding a module to the classpath of your
application is enough to activate its functionality; no `@Import` or scanning annotations are required.
The main beans registered by the auto-configurations can be replaced by declaring your own bean of the same type.
See Chapter 5,
["Configuration and Auto-Configuration"](./05-infrastructure-integration.md#configuration-and-auto-configuration),
for details.

- `de.kiwi-framework:kiwi-api` The Kiwi API module contains the interfaces and base classes shared among all other
  modules: entities and entity IDs, commands, queries and events, entity schemas and fieldsets, access-control, and
  the service interfaces implemented by the other modules. It is required for the application to interact with the
  framework, and all other modules depend on it. The API module exposes Spring Security core, Spring Data JPA,
  Jakarta Persistence and Jackson as API dependencies. It contains no auto-configuration.
- `de.kiwi-framework:kiwi-framework` The Kiwi Framework module (package `de.kiwi.framework`) implements the API module
  and provides most of the core functionality. It auto-configures:
    - the query-schema service, generic entity commands and queries, analytical reports, fieldset validation, the
      user descriptor provider, the transaction service and the application clock
      (`KiwiFrameworkAutoConfiguration`),
    - the command-, query- and event-bus, including the registration of handler and subscriber beans
      (`KiwiBusAutoConfiguration`),
    - the e-mail sender service with its Mustache template renderer (`KiwiMailAutoConfiguration`),
    - the web layer in servlet applications: generic entity and analytical report endpoints, exception handling and
      pagination (`KiwiWebAutoConfiguration`, can be disabled with `kiwi.web.enabled=false`),
    - OpenAPI documentation of the Kiwi endpoints, if SpringDoc is on the classpath (`KiwiOpenApiAutoConfiguration`,
      can be disabled with `kiwi.web.open-api.enabled=false`),
    - optional HTTP Basic security for the actuator management endpoints
      (`KiwiManagementSecurityAutoConfiguration`, enabled with `kiwi.web.management-security.enabled=true`).

  This module should be included in the main application module, but does not need to be included in every
  application module.
- `de.kiwi-framework:kiwi-storage` The Kiwi Storage module provides a file entity, a database schema, and
  implementations of the API module `FileStorageService` interface. It auto-configures the file entity and its
  repository, and the storage provider selected by the property `kiwi.storage.provider`: `none`, `data-uri`, `s3`, or
  `local`. The property is required, unless the application declares its own `FileStorageService` bean. With the `local` provider, it also serves the stored files using signed URIs
  (see [Chapter 5: "File Storage"](./05-infrastructure-integration.md#file-storage)).
- `de.kiwi-framework:kiwi-audit-log` The Kiwi Audit-Log module (package `de.kiwi.auditlog`) provides an
  audit-log-entry entity, a database schema, and an implementation of the API module `AuditLoggerService` interface,
  which stores domain events and domain exceptions in the database. It is enabled by default and can be disabled with
  `kiwi.audit-log.enabled=false` (see [Chapter 5: "Audit Log"](./05-infrastructure-integration.md#audit-log)).
- `de.kiwi-framework:kiwi-mjml` The Kiwi MJML module (package `de.kiwi.mjml`) is a plug-in that extends the e-mail
  template rendering of Kiwi, allowing it to render [MJML Templates](https://mjml.io/) into HTML e-mail content.
  It adds its renderer to the e-mail template rendering pipeline, and can be disabled with
  `kiwi.mail.mjml.enabled=false`
  (see [Chapter 5: "Rendering and Sending E-Mails"](./05-infrastructure-integration.md#rendering-and-sending-e-mails)).

> Note: Up to Kiwi 1.4, the framework module was published as `kiwi-bundle`. If you upgrade an existing application,
> replace the artifact ID with `kiwi-framework`.

## Project Application Architecture

Kiwi-Server is an opinionated framework, which comes with its own best practices and paradigms based on principles
like [Domain-Driven Design](https://en.wikipedia.org/wiki/Domain-driven_design),
the [SOLID Principles](https://en.wikipedia.org/wiki/SOLID),
[CQRS](https://en.wikipedia.org/wiki/Command_Query_Responsibility_Segregation),
and [Hexagonal Architecture](https://en.wikipedia.org/wiki/Hexagonal_architecture_(software)).

As such, applications built with Kiwi Server should adhere to the following structure:

- Each application module contains a base package, named after the module's group-id
- The base package contains a `domain` package, containing the application core: entities, entity schemas and
  fieldsets, commands, queries, and events
  (see [Chapter 2: "Domain Entities and Schema"](./02-domain-schema.md))
- The base package contains an `application` package, containing the application handlers, use-case logic, service- and
  repository-interfaces (see [Chapter 3: "Application Commands, Queries and Events"](./03-application-command-query.md))
- The base package contains an `infrastructure` package, containing the application infrastructure, configuration
  classes, and service implementations
  (see [Chapter 5: "Infrastructure and Integration"](./05-infrastructure-integration.md))
- The base package contains a `web` package, containing the application web-controllers and controller-advice classes
  (see [Chapter 6: "Web MVC and Endpoints"](./06-web-mvc-endpoints.md))

The dependencies point inwards: the `domain` package does not depend on any of the other packages, the `application`
package only depends on the `domain`, and the `infrastructure` and `web` packages connect the application core to the
outside world.

The main application class annotated with `@SpringBootApplication` is placed in the base package. Spring Boot scans
the base package and its sub-packages for components, JPA entities and Spring Data repositories, so all of the
packages above are found automatically.

For a full overview of the recommended architecture for a project application module, see
[Appendix: "Application Module Architecture"](./99-appendix.md#application-module-architecture).
All components shown in this diagram will be explained throughout the chapters of this guide.

## Set up a new Project Application

Here is how you can start development on a new project application. This guide builds a Todo-List application, the
complete version of which is the demo application `demo/todo-app` in the Kiwi-Server repository.

### Start with Spring Initializr

The easiest way to begin a new project is using the [**Spring Initializr**](https://start.spring.io/) service.
This tool, which may be integrated into your IDE, will help you quickly set up an initial project to begin with.

Recommended initializr settings:

- Project: **Gradle - Kotlin**
- Language: **Kotlin**
- Spring Boot: Latest **4.1.x**, check for `spring-boot` in [gradle/libs.versions.toml](../gradle/libs.versions.toml)
  for the version Kiwi was built with
- Packaging: **jar**
- Configuration: **YAML**
- Java: **21** or above

In the following sections, it is assumed the project group-id is `com.example` and name/artifact-id is `example`,
with the base package also defined as `com.example`.

Starter dependencies for a Kiwi-Server application:

- Developer Tools:
    - **Spring Boot DevTools** for dynamic restarting on compile
    - **Docker Compose Support** for starting service containers, like the database, during development
    - **Spring Configuration Processor** for auto-completion of application config properties
- Web:
    - **Spring Web** for MVC controllers and the embedded Tomcat web server (`spring-boot-starter-webmvc`)
    - (Optional) **WebSocket** for servlet-based websocket applications using STOMP
- Security:
    - **Spring Security** for user authentication and basic authorization (`spring-boot-starter-security`)
    - (Optional) **OAuth2 Client** for OAuth2/OpenID-Connect login integration
    - (Optional) **OAuth2 Resource Server** for authenticating requests with JWT bearer tokens issued by an external
      authorization server
    - (Optional) **Spring LDAP** for integrating user authentication against LDAP directories
- SQL:
    - **Spring Data JPA** for persistence and querying using JPA/Hibernate (`spring-boot-starter-data-jpa`)
    - **Liquibase Migration** for application-controlled database migration (`spring-boot-starter-liquibase`)
    - Your preferred database driver, usually the **MySQL**, **MariaDB** or **PostgreSQL Driver**
- I/O:
    - (Optional) **Java Mail Sender** for sending e-mails over SMTP
- Ops:
    - (Optional) **Spring Boot Actuator** for health- and monitoring-endpoints
    - Observability adapters like Prometheus can be added for integrating other monitoring tools
- Testing:
    - **Testcontainers** to support temporary service containers during tests, with the Testcontainers module for
      your database, e.g. `org.testcontainers:testcontainers-mysql`
- Further dependencies can be used at your discretion, but are not specifically supported by Kiwi Server

Kiwi does not authenticate users itself. Authentication is configured by your application using Spring Security, e.g.
with form login, HTTP Basic, or as an OAuth2 resource server; Kiwi reads the authenticated user from the Spring
Security context.
See [Chapter 5: "Spring Security Integration"](./05-infrastructure-integration.md#spring-security-integration).

After generating a basic Spring Boot project and opening it in your IDE, open the `build.gradle.kts` file in the
project root directory.

> Note: Spring Boot 4 renamed several starters. If you copy dependencies from older projects, use
> `spring-boot-starter-webmvc` instead of `spring-boot-starter-web`, and `spring-boot-starter-liquibase` instead of a
> plain `org.liquibase:liquibase-core` dependency: with only `liquibase-core`, Spring Boot 4 no longer runs the
> database migrations on startup.

### Integrate the Kiwi-Server Framework

To use the Kiwi-Server Framework for your project, edit the `build.gradle.kts` build script.
The following build script is a complete example, based on the build of the demo Todo-List application:

```kotlin
plugins {
    // (1) Kotlin, KAPT for annotation processing, and the Kotlin compiler plugins for Spring and JPA
    kotlin("jvm") version "2.3.21"
    kotlin("kapt") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"

    // (2) Spring Boot and dependency management
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // (3) Choose a Kiwi Server version; this can also be a property in gradle.properties
    val kiwiVersion = "1.5.0"

    // (4) Add Kiwi artifacts as dependencies
    // API Module: Required in *every* application module, provides the Kiwi interfaces
    implementation("de.kiwi-framework:kiwi-api:$kiwiVersion")
    // Framework Module: Required in the main application module, implements the Kiwi functionality
    implementation("de.kiwi-framework:kiwi-framework:$kiwiVersion")
    // [Optional] Storage Module: Provides FileStorageService implementations and the file entity
    implementation("de.kiwi-framework:kiwi-storage:$kiwiVersion")
    // [Optional] Audit-Log Module: Provides an AuditLoggerService implementation and the audit-log-entry entity
    implementation("de.kiwi-framework:kiwi-audit-log:$kiwiVersion")
    // [Optional] MJML Module: Provides an MJML e-mail template renderer
    implementation("de.kiwi-framework:kiwi-mjml:$kiwiVersion")

    // (5) Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // (6) Annotation processors: configuration property metadata, and the static JPA metamodel
    kapt("org.springframework.boot:spring-boot-configuration-processor")
    kapt("org.hibernate.orm:hibernate-processor")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        javaParameters = true
    }
}

// (7) Make JPA entity classes open, as required by Hibernate for lazy-loading proxies
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

kapt {
    correctErrorTypes = true
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

> To see which versions of Kiwi-Server are available, check the
> `maven-metadata.xml` file of the `de.kiwi-framework` artifacts provided by the Maven Repository on Maven Central.

The Kiwi modules depend on each other transitively: `kiwi-framework`, `kiwi-storage` and `kiwi-audit-log` include
`kiwi-api`, and `kiwi-mjml` includes `kiwi-framework`. Declaring `kiwi-api` explicitly makes clear that your code uses
its interfaces, which is especially useful in application modules that don't include the other modules.

The `hibernate-processor` annotation processor generates the static JPA metamodel classes (e.g. `TodoItem_` for the
`TodoItem` entity) that are used for type-safe criteria queries, filters and reports. Since Hibernate 7 it replaces the
former `hibernate-jpamodelgen` artifact. The `spring-boot-configuration-processor` generates the metadata for your own
`@ConfigurationProperties` classes, which enables auto-completion in your IDE. Both processors run with KAPT, as they
process Kotlin sources.

If you want to generate OpenAPI documentation of your endpoints, also add SpringDoc, e.g.
`org.springdoc:springdoc-openapi-starter-webmvc-ui`. Kiwi then documents its generic endpoints automatically,
see [Chapter 6: "OpenAPI Documentation"](./06-web-mvc-endpoints.md#openapi-documentation).

### Main application class

Since all Kiwi modules are configured by Spring Boot auto-configuration, the main application class is a plain
Spring Boot application. In the example application, this is `src/main/kotlin/com/example/ExampleApplication.kt`:

```kotlin
package com.example

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ExampleApplication

fun main(args: Array<String>) {
    runApplication<ExampleApplication>(*args)
}
```

Spring Boot scans the package of this class, `com.example`, and its sub-packages for your JPA entities and
repositories. The Kiwi modules register their own entities and repositories, like the file entity of the storage
module and the audit-log-entry entity of the audit-log module, in addition to your application's packages.

> Note: Kiwi 1.4 and earlier required `@Import(KiwiFrameworkConfiguration::class, ...)`, `@EntityScan` and
> `@EnableJpaRepositories` on the application class. Remove these when upgrading: the configuration classes no longer
> exist. You only need `@EntityScan` (from `org.springframework.boot.persistence.autoconfigure`) or
> `@EnableJpaRepositories` if your entities or repositories are located outside the package of the application class;
> the Kiwi module entities are still registered in that case.

### Database migrations

Kiwi applications manage their database schema with Liquibase. Hibernate only validates the schema on startup.

Create the Liquibase master changelog file `src/main/resources/db/changelog/db.changelog-master.yaml`, which is the
default location Spring Boot looks for. This file must include the changelogs of the Kiwi modules included in your
application, followed by your own changelogs:

```yaml
databaseChangeLog:
  ## (1) Kiwi module database migrations
  # Kiwi Storage migrations, if the storage module is included in the application
  - include: { file: "classpath:db/changelog/db.storage-master.xml" }
  # Kiwi Audit-Log migrations, if the audit-log module is included in the application
  - include: { file: "classpath:db/changelog/db.audit-log-master.xml" }
  ## (2) Application migrations, either directly or as file-includes
  #- include: { relativeToChangelogFile: true, file: "db.example-01.yaml" }
```

The Kiwi changelogs contain all migrations of the respective module, including those of newer Kiwi versions. For
example, the audit-log changelog of Kiwi 1.5 migrates the event details of existing audit-log entries to a JSON column.
The database-specific migrations in these changelogs support MySQL, MariaDB and
PostgreSQL.

Your own changelogs will be added in [Chapter 2: "Domain Entities and Schema"](./02-domain-schema.md).

### Application configuration

If you did not choose YAML configuration in the Spring Initializr, replace the generated
`src/main/resources/application.properties` file with a YAML configuration file: `src/main/resources/application.yml`.

> Note: If you don't use YAML for application configuration, the equivalent properties are written as property-paths
> joined with dots. For example, `spring: { application: { name: "example" } }` is equivalent to
> the property `spring.application.name=example`.

In the application configuration `src/main/resources/application.yml`, set up the basic configuration properties and
the Kiwi Framework modules:

```yaml
## (1) Basic Spring, Spring-Data JPA/Hibernate properties
spring:
  application:
    name: example
  # Spring-Data JPA/Hibernate configuration
  jpa:
    # Set to `true` to enable SQL console logging
    show-sql: false
    # Always keep this `false`, as the associated feature is not required and could degrade performance
    # (See the "OSIV Anti-Pattern": https://vladmihalcea.com/the-open-session-in-view-anti-pattern/ )
    open-in-view: false
    hibernate:
      # Validate database schema on startup
      # NOTE: If configured wrong, Hibernate may attempt to create its own database schema,
      # or even drop or break the existing schema. This is a notorious foot-gun!
      ddl-auto: validate
  # Liquibase master changelog, this is the default location
  liquibase:
    change-log: "classpath:db/changelog/db.changelog-master.yaml"

kiwi:
  ## (2) Kiwi Audit-Log configuration properties
  audit-log:
    # Enabled by default, set to `false` to disable audit-logging
    enabled: true
  ## (3) Kiwi Storage configuration properties
  storage:
    # Choose a storage provider (required): "none", "data-uri", "s3", or "local"
    provider: local
    local:
      # Directory in which the local storage provider stores files
      parent-directory: "storage"
      token:
        # Base64-encoded secret key for signing local file URIs, at least 256 bits
        # Can be generated in bash: head -c 32 /dev/random | base64
        secret-key: "... insert secret-key here ..."
```

> Note: The local storage provider requires a secret key in `kiwi.storage.local.token.secret-key`, otherwise the
> application fails to start. Generate a key for your application, and don't commit production keys to version
> control: provide them e.g. as environment variable `KIWI_STORAGE_LOCAL_TOKEN_SECRETKEY`.
> The storage provider is required, the application fails to start without it (unless it declares its own
> `FileStorageService` bean). If your application does not store files, set `kiwi.storage.provider` to `none`, or
> don't include the storage module at all.

The Kiwi framework module works without any further configuration. The demo application configures a few more
properties, e.g. the mail server and the Docker Compose file, see `demo/todo-app/src/main/resources/application.yml`.

For a full reference of available Kiwi module configuration properties,
see [Chapter 5: "Configuration Properties"](./05-infrastructure-integration.md#configuration-properties).

Congratulations, you're now ready to start development on your new web-server application!

### First application run

The application needs a database to start. If you selected **Docker Compose Support** and a database driver in the
Spring Initializr, the generated `compose.yaml` file in the project root directory defines a database container, which
Spring Boot starts automatically when the application is run. Make sure Docker is running on your machine.
Otherwise, configure the connection to your database with the `spring.datasource.*` properties.

To verify that everything works correctly, run the following command within the project root directory:

```shell
./gradlew bootRun
```

On startup, Liquibase applies the database migrations of the Kiwi modules. The application server should now be
running, with a message confirming:

> Tomcat started on port 8080 (http) with context path '/'  
> Started ExampleApplicationKt in (...) seconds

If you now navigate to http://localhost:8080/, Spring Security asks you to log in: without further security
configuration, Spring Boot creates a single user named `user`, and logs its generated password on startup
("Using generated security password: ..."). After logging in, you'll see a whitelabel error page with status
`type=Not Found, status=404`. This means your application is running, but has no controller mappings yet.

To check if tests are working correctly, run the following command within the project root directory:

```shell
./gradlew test
```

This will execute the application tests, and should report a success message upon completion:

> BUILD SUCCESSFUL in (...)s

If the application fails to start, see [Chapter 9: "Troubleshooting"](./09-troubleshooting.md) for common problems.

---

Next up, [Chapter 2: "Domain Entities and Schema"](./02-domain-schema.md) is about defining entities, entity schema, and
fieldsets to add data, logic and the domain of your application core.
