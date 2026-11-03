# 6. Web MVC and Endpoints - Kiwi Server

The HTTP API of a Kiwi application consists of two kinds of endpoints:

- Your own **Spring MVC controllers**, which translate HTTP requests into commands and queries and submit them to the
  command- and query-bus
- **Generated endpoints**, which the Kiwi-Server Framework registers automatically for generic entity queries and
  commands, and for analytical report queries

Both are set up by the `KiwiWebAutoConfiguration`, which is active in servlet-based web applications (Spring Web MVC)
and can be disabled as a whole using `kiwi.web.enabled=false`. Besides the generated endpoints, it registers the Kiwi
exception handling, the pagination settings, the `UserDescriptor` controller argument, and the `EntityId` converters
described in this chapter. See [Chapter 5: "Configuration and
Auto-Configuration"](./05-infrastructure-integration.md#configuration-and-auto-configuration) for the
autoconfiguration in general.

> Note: Request paths are matched exactly. Trailing slashes are not supported, so `GET /TodoList/` does _not_ match
> the endpoint `GET /TodoList`. This applies to the generated endpoints as well as to your own controllers.

## Spring MVC Controllers

Following the application architecture
(see [Chapter 1: "Project Application Architecture"](./01-introduction.md#project-application-architecture)),
controllers are located in the `web` package of an application module. A controller should not contain business logic:
it converts the request into a command or query, submits it with the user of the current request, and converts the
result into a response.

The demo todo-app has a use-case that is not covered by the generic commands: moving a TodoItem into another TodoList.
The `MoveTodoItemToList` command (see
[Chapter 3: "Command-, Query- and Event-Bus"](./03-application-command-query.md#command--query--and-event-bus)) is
exposed by the following controller:

```kotlin
package de.kiwi.demo.web

@RestController
@RequestMapping("/TodoItem")
@Tag(name = "kiwi.TodoItem") // Group with the generated TodoItem endpoints in the OpenAPI documentation
class TodoItemController(
    private val bus: CommandQueryBus,
) {
    @Operation(summary = "Move a TodoItem into a specific TodoList")
    @PatchMapping(path = ["/{itemId}/TodoList/{listId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun moveItemToList(
        @PathVariable("itemId") itemId: TodoItemId,
        @PathVariable("listId") listId: TodoListId,
        user: UserDescriptor,
    ): ResponseEntity<Unit> {
        val command = MoveTodoItemToList(itemId = itemId, targetListId = listId)
        val result = bus.dispatch(command, user)
        return ResponseEntity.ok(result)
    }
}
```

The example shows the building blocks Kiwi provides for controllers:

- **Buses**: The `CommandQueryBus` bean combines the `CommandBus` and the `QueryBus`. You can also inject the
  `CommandBus` or `QueryBus` directly, if a controller only needs one of them: these are the primary beans of their
  types, while the `CommandQueryBus` is only injected where it is requested by its own type.
- **UserDescriptor arguments**: A controller method parameter of type `UserDescriptor` is resolved to the user of the
  current request by the `UserDescriptorArgumentResolver`, using the `UserDescriptorProvider` bean. If no user is
  authenticated, the argument is `UserDescriptor.ANONYMOUS`
  (see [Chapter 3: "User-Descriptor Interface"](./03-application-command-query.md#user-descriptor-interface)).
- **EntityId conversion**: Path variables and request parameters of an `EntityId` type (like `TodoItemId`) are
  converted from their string or numeric representation. Kiwi registers the converters
  `StringToEntityIdConverterFactory`, `NumberToEntityIdConverterFactory`, `EntityIdToStringConverter` and
  `EntityIdToNumberConverter` with Spring MVC.
- **Pagination parameters**: Kiwi configures Spring Data's `Pageable` and `Sort` controller arguments with the same
  parameter names and limits as the generated endpoints (`_page`, `_size` and `_sort` by default, see
  ["Pagination and sorting"](#pagination-and-sorting)).

### Commands, queries and DomainResult values

The buses offer different ways to execute a command or query:

- `CommandQueryBus.dispatch(interaction, user)` executes a command or a query and returns its result value. For
  commands, only the value is returned, the domain events are dropped.
- `CommandBus.submit(command, user)` returns the full `DomainResult<R>` of a command, containing the result value and
  the recorded domain events
  (see [Chapter 3: "DomainResult Convention"](./03-application-command-query.md#domainresult-convention)).
- `QueryBus.resolve(query, user)` returns the result of a query.

Return the result _value_ to the client, not the `DomainResult` itself: domain events usually reference entities and
are not meant to be serialized into a response. If a controller needs to inspect the events, use `submit` and pick
the events of interest:

```kotlin
private val log = slf4j<TodoListController>()

@RestController
@RequestMapping("/TodoList")
@Tag(name = "kiwi.TodoList")
class TodoListController(
    private val bus: CommandQueryBus,
    private val objectMapper: ObjectMapper,
) {
    @PostMapping("/{listId}/items", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun addItem(
        @PathVariable listId: TodoListId,
        @RequestBody body: ObjectNode,
        user: UserDescriptor,
    ): ResponseEntity<JsonNode> {
        // Use the generic create command of TodoItem, adding the list ID of the path to the request body
        val command = CreateTodoItem()
        command.bind(body.deepCopy().put("list", listId.toString()), objectMapper)

        val result = bus.submit(command, user)
        val created = result.findByType<TodoItemCreated>()
        log.info("Created TodoItem ${created?.todoItem?.id} in TodoList $listId")
        return ResponseEntity.status(HttpStatus.CREATED).body(result.value)
    }
}
```

`slf4j<T>()` is a logger helper of the `de.kiwi.api.util` package. The result value of the generic create command is
the created entity as `JsonNode`, extracted using the command's `detailsProvider`.

Queries are executed the same way. The following endpoint of the `TodoItemController` returns a single TodoItem
using the `short` fieldset. Generic entity queries return a `PageResult<JsonNode>`, which is empty if no entity with
the given ID exists:

```kotlin
@GetMapping("/{itemId}/short", produces = [MediaType.APPLICATION_JSON_VALUE])
fun shortItem(@PathVariable itemId: TodoItemId, user: UserDescriptor): ResponseEntity<JsonNode> {
    val query = TodoItemQuery().apply {
        id = itemId
        fieldset = TodoItemFieldsets.short
    }
    val item = bus.resolve(query, user).firstOrNull()
    return item?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
}
```

> Note: `JsonNode` and `ObjectNode` are Jackson 3 types (packages `tools.jackson.databind` and
> `tools.jackson.databind.node`).

Exceptions thrown by a controller, for example a `DomainException` thrown by a command handler, are converted into
error responses as described in ["Exception Handling"](#exception-handling). Authorization belongs into the commands
and queries (`preAuthorize`, access control), not into the controller, so the same rules apply to the generated
endpoints and to your own controllers.

### Functional endpoints

Kiwi registers its generated endpoints as Spring MVC router functions (`RouterFunction` beans, see
[Functional Endpoints](https://docs.spring.io/spring-framework/reference/web/webmvc-functional.html) in the Spring
documentation): `genericEntityRouter` for the generic entity endpoints and `analyticalReportRouter` for the report
endpoints.

You can define your own router functions as well. Router functions are not handled by `@RestControllerAdvice` classes
and have no argument resolvers, so

- get the current user from the `UserDescriptorProvider`,
- convert `EntityId` path variables yourself, for example using `EntityId.fromString<TodoListId>(value)`,
- and handle exceptions with an `onError` handler, using the `ExceptionResponseManager` bean to create the same error
  responses as the rest of the API.

```kotlin
@Configuration(proxyBeanMethods = false)
class TodoListStatsRoutes(
    private val bus: CommandQueryBus,
    private val userDescriptorProvider: UserDescriptorProvider,
    private val exceptionResponseManager: ExceptionResponseManager,
) {
    @Bean
    fun todoListStatsRouter() = router {
        // GET /TodoList/{id}/stats => Item counts of a single TodoList
        GET("/TodoList/{id}/stats") { request ->
            val listId = EntityId.fromString<TodoListId>(request.pathVariable("id"))
            val query = TodoItemReportQuery().apply {
                dimensions.add("listId")
                metrics.addAll(listOf("total", "completed", "incomplete"))
                slices["listId"] = Slice(eq = listId)
            }
            val report = bus.resolve(query, userDescriptorProvider.currentUser())
            ServerResponse.ok().body(report)
        }

        // Create Kiwi error responses for all exceptions of this router
        onError<Exception> { ex, _ ->
            val response = exceptionResponseManager.resolveException(ex)
            ServerResponse.status(response.status).body(response.body)
        }
    }
}
```

> Note: In Spring MVC's default configuration, annotated controllers are consulted before router functions. A
> `@RequestMapping` with the same path and HTTP method as a generated endpoint therefore takes precedence over the
> generated endpoint.

## Exception Handling

Exceptions are converted into HTTP error responses by the `ExceptionResponseManager`. It is used in two places:

- The `KiwiControllerAdvice`, a `@RestControllerAdvice(annotations = [RestController::class])` with an
  `@ExceptionHandler(Exception::class)` method. It applies to **all** `@RestController` classes of the application,
  including your own controllers.
- The `onError` handlers of the generated router functions (generic entity and report endpoints).

This section describes the current behavior. Only `Exception`s are handled: `Error`s like `AssertionError` are handled
by neither of the two and fall through to Spring Boot's default error handling.

### Error response body

Every handled exception results in a JSON body with three properties:

```json
{
  "message": "unknown-column-filter-operator",
  "arguments": {
    "columnName": "title",
    "operator": "foo"
  },
  "errorType": "de.kiwi.framework.web.DynamicColumnFilter$UnknownColumnFilterOperator"
}
```

- `message`: A machine-readable error code, which clients can use to look up a translated error message
- `arguments`: Details of the error, depending on the error code
- `errorType`: The type of the error, usually the fully qualified exception class name

The `ErrorResponse.ErrorBody` class defines this body.

### Built-in exception mappings

The `KiwiBuiltinExceptionResponseResolverProvider` registers the following mappings:

| Exception                                   | Status                    | `message`                  |
|---------------------------------------------|---------------------------|----------------------------|
| `DomainException` (and subclasses)          | The exception's `status`  | The exception's `code`     |
| `IllegalArgumentException`                  | 400 Bad Request           | `illegal-argument`         |
| `IllegalStateException`                     | 400 Bad Request           | `illegal-state`            |
| `HttpMessageNotReadableException`           | 400 Bad Request           | `message-not-readable`     |
| `DataIntegrityViolationException`           | 500 Internal Server Error | `data-integrity-violation` |
| `AuthenticationException` (Spring Security) | 401 Unauthorized          | `authentication-exception` |
| `AccessDeniedException` (Spring Security)   | 403 Forbidden             | `access-denied`            |
| Any other exception                         | 500 Internal Server Error | `unknown-exception`        |

- For a `DomainException`, `arguments` contains the exception's `args`, and `errorType` its `eventName` (the class name
  by default). This is the preferred way to report errors of your application: define `DomainException` subclasses
  with a `Status` (`BadRequest`, `Unauthorized`, `Forbidden`, `NotFound`, `MethodNotAllowed`, `ImATeapot`,
  `InternalError` or `Other(value)`), an error code and arguments.
- For the other built-in mappings, `arguments` contains the exception message as `reason`.
- Unknown exceptions are logged as errors. Their `arguments` contain the exception message (`reason`) and the simple
  class name (`type`). This also applies to exceptions raised by Spring MVC itself while handling a request of a
  `@RestController`, for example a `MethodArgumentTypeMismatchException`. Register a resolver for such exceptions if you
  want them mapped to a different status.

> Note: Kotlin's `require(...)` and `check(...)` throw `IllegalArgumentException` and `IllegalStateException`, which
> both result in a `400 Bad Request` response.

### Custom exception resolvers

The `ExceptionResponseManager` collects its resolvers from all beans implementing
`ExceptionResponseManager.ResolverProvider`. For an exception, it uses the resolver of the **most specific** matching
exception class: a resolver for `OptimisticLockingFailureException` takes precedence over a resolver for
`RuntimeException`.

To add resolvers, declare a `ResolverProvider` bean. `ExceptionResponseResolver<E> { ... }` creates a resolver for the
exception type `E`, which returns an `ExceptionResponseResolver.ResolvedError`:

```kotlin
@Configuration(proxyBeanMethods = false)
class DemoExceptionResolvers {
    @Bean
    fun demoExceptionResolverProvider() = ExceptionResponseManager.ResolverProvider {
        listOf(
            ExceptionResponseResolver<OptimisticLockingFailureException> { ex ->
                ExceptionResponseResolver.ResolvedError(
                    status = HttpStatus.CONFLICT,
                    message = "concurrent-modification",
                    arguments = mapOf("reason" to ex.message),
                    errorType = ex.javaClass.name,
                )
            },
        )
    }
}
```

Declare resolvers for specific exception types rather than for the exact exception classes of the built-in mappings:
the order between several resolvers for the _same_ exception class is not defined.

### Replacing the exception handling

The `KiwiControllerAdvice` and the `ExceptionResponseManager` beans are only created if no bean of the same type
exists, so you can replace them with your own beans. Other options:

- An `@ExceptionHandler` method declared within a controller class takes precedence over the controller advice for
  that controller.
- Your own `@RestControllerAdvice` classes are consulted before the `KiwiControllerAdvice` if they declare an order,
  for example `@Order(Ordered.HIGHEST_PRECEDENCE)`, because the Kiwi advice has the lowest precedence and handles all
  exceptions.

## Generic Entity Endpoints

For each entity with generic queries and commands
(see [Chapter 3: "Generic Commands and Queries"](./03-application-command-query.md#generic-commands-and-queries)),
Kiwi registers HTTP endpoints. No controller code is necessary.

| Method   | Path                | Registered if the entity has ... | Handled by                    |
|----------|---------------------|----------------------------------|-------------------------------|
| `GET`    | `/<entity>`         | a `GenericEntityQuery` class     | Page of entities              |
| `GET`    | `/<entity>/{id}`    | a `GenericEntityQuery` class     | Single entity by ID           |
| `POST`   | `/<entity>`         | a create command class           | Create an entity              |
| `PATCH`  | `/<entity>/{id}`    | an update command class          | Update an entity              |
| `DELETE` | `/<entity>/{id}`    | a delete command class           | Delete an entity              |
| `GET`    | `/<entity>/_schema` | an `EntitySchema`                | Schema metadata of the entity |

For the demo todo-app, this results in the endpoints `GET /TodoList`, `GET /TodoList/{id}`, `POST /TodoList`,
`PATCH /TodoList/{id}`, `DELETE /TodoList/{id}` and `GET /TodoList/_schema`, and the same endpoints for `TodoItem`.

The generated endpoints execute the generic queries and commands with the user of the current request, so their
`preAuthorize` functions and access control rules apply
(see [Chapter 4: "Access Control"](./04-querying-access-control.md#access-control)). Securing the paths themselves,
for example requiring an authenticated user, is the task of the application's Spring Security configuration
(see [Chapter 5: "Spring Security Integration"](./05-infrastructure-integration.md#spring-security-integration)).

### Entity path names

The `<entity>` path segment is derived from the JPA entity name, which is the simple class name unless declared
otherwise with `@Entity(name = ...)`. The property `kiwi.web.naming-strategy` determines which variants of the entity
name are registered as paths:

| Naming strategy | Path for entity `TodoItem` |
|-----------------|----------------------------|
| `CamelCase`     | `/TodoItem`                |
| `SnakeCase`     | `/todo_item`               |
| `Lowercase`     | `/todoitem`                |

By default, all three variants are registered. To register only the entity name itself:

```yaml
kiwi:
  web:
    naming-strategy: CamelCase
```

The OpenAPI documentation only lists the entity name itself (`/TodoItem`).

### Enabling and disabling

| Property                      | Default | Effect                                                                  |
|-------------------------------|---------|-------------------------------------------------------------------------|
| `kiwi.web.enabled`            | `true`  | `false` disables the whole Kiwi web layer, including exception handling |
| `kiwi.web.generic-controller` | `true`  | Disables the generic entity endpoints if `false`                        |
| `kiwi.web.report-controller`  | `true`  | Disables the analytical report endpoints if `false`                     |

Disabling the endpoints does not affect the generic queries and commands themselves: they can still be executed using
the buses, for example from your own controllers.

### Query parameters

The query endpoints `GET /<entity>` and `GET /<entity>/{id}` accept the following parameters:

| Parameter     | Endpoints   | Description                                                                |
|---------------|-------------|----------------------------------------------------------------------------|
| `_fieldset`   | list, by ID | Name of the fieldset (case-insensitive), `default` if omitted              |
| `_fields`     | list, by ID | Dynamic fieldset expression, takes precedence over `_fieldset` (see below) |
| `_accelerate` | list, by ID | `true` to receive an accelerated response (see below)                      |
| `_page`       | list        | Zero-based page number, `0` if omitted                                     |
| `_size`       | list        | Page size                                                                  |
| `_sort`       | list        | Sort order, repeatable                                                     |
| other         | list        | Entity filter properties and column filters                                |

The fieldset determines which fields of the entity are returned (see
[Chapter 2: "Entity Schema and Fieldsets"](./02-domain-schema.md#entity-schema-and-fieldsets)). An unknown fieldset
name is rejected with `400 Bad Request` (`no-such-fieldset`), with the available fieldset names as argument.

### Pagination and sorting

List requests are always paginated. The page size defaults to `kiwi.pagination.default-page-size` (50), and larger
sizes than `kiwi.pagination.max-page-size` (1000) are reduced to this maximum. A page size below `1` falls back to the
default page size.

The sort parameter has the format `<field>` or `<field>,<direction>`, with the direction `asc` (default) or `desc`. It
can be repeated to sort by several fields. The field names refer to the columns of the entity schema (case-insensitive);
to-one relations and subtypes can be traversed with dots:

```http
GET /TodoItem?_page=0&_size=20&_sort=status,desc&_sort=order
GET /TodoItem?_sort=list.title
```

An unknown sort field is rejected with `400 Bad Request` (`invalid-query-sort`).

The parameter names can be changed using configuration properties. They apply to the generated endpoints and to the
`Pageable`/`Sort` arguments of your own controllers:

```yaml
kiwi:
  pagination:
    default-page-size: 50
    max-page-size: 1000
    page-number-parameter: "_page"
    page-size-parameter: "_size"
    sort-parameter: "_sort"
```

### Entity filter parameters

If an entity filter class is declared for the entity
(see [Chapter 4: "Query Schema and Generic Querying"](./04-querying-access-control.md)), request parameters are bound
to the filter properties with the same name. Values are converted using Spring's conversion service, including the
`EntityId` converters.

For example, with a `TodoItemFilter` declaring a `searchContent` filter property, the request
`GET /TodoItem?searchContent=milk` sets `filter.searchContent = "milk"` on the generic `TodoItemQuery`.

### Column Filter Parameters

Request parameters that match the name of a column of the entity schema (case-insensitive) filter the entities by this
column, without declaring an entity filter property
(see [Chapter 4: "Column Filters"](./04-querying-access-control.md#column-filters)). A column filter parameter has one
of two forms:

- `<column>=<values>` uses the default operator of the column, which is `in` for all built-in column types
- `<column>:<operator>=<values>` uses the given operator

Multiple column filters, also several operators for the same column, are combined with a logical "and":

```http
GET /TodoItem?status=Open
GET /TodoItem?status=Open,Done
GET /TodoItem?listId=<listId>&order:ge=1&order:lt=10
GET /TodoItem?content:null=false
```

The built-in operators are:

| Operator     | Condition                                        | Values                                |
|--------------|--------------------------------------------------|---------------------------------------|
| `in`         | Column value is one of the values                | One or more                           |
| `not`        | Column value is none of the values               | One or more                           |
| `eq`         | Column value is equal to the value               | Exactly one                           |
| `ne`         | Column value is not equal to the value           | Exactly one                           |
| `null`       | `true`: column is null, `false`: is not null     | Boolean, only the first value is used |
| `gt`, `ge`   | Column value is greater than (or equal to) value | Exactly one                           |
| `lt`, `le`   | Column value is less than (or equal to) value    | Exactly one                           |
| `contains`   | String column contains the value                 | Exactly one                           |
| `startsWith` | String column starts with the value              | Exactly one                           |

Operators taking one or more values accept the parameter repeatedly (`status=Open&status=Done`) or once with
comma-separated values (`status=Open,Done`). In a single value, a comma can be escaped using a backslash (`\,`). The
`contains` and `startsWith` operators escape the wildcard characters of the value, so the value is matched literally.

Which operators a column supports depends on the `ColumnFilterSet` of its column type
(see [Chapter 2: "Entity Schema and Fieldsets"](./02-domain-schema.md#entity-schema-and-fieldsets)):

| Filter set                              | Operators                              | Used by                           |
|-----------------------------------------|----------------------------------------|-----------------------------------|
| `StandardColumnFilters.basic`           | `in`, `not`, `eq`, `ne`, `null`        | Entity IDs, enums, `UUID`, `URI`  |
| `StandardColumnFilters.default`         | `basic` and `gt`, `ge`, `lt`, `le`     | All other column types by default |
| `StandardColumnFilters.patternMatching` | `default` and `contains`, `startsWith` | `LikeStringColumnType`            |
| `StandardColumnFilters.empty`           | none                                   | Unfilterable column types         |

For example, `GET /TodoItem?title:contains=milk` requires the `title` column of the `TodoItemSchema` to be declared
with `type = LikeStringColumnType`. You can also assign a filter set to a column type using `withFilters`, or define
your own operators (see
[Chapter 8: "Custom ColumnFilterOperators"](./08-advanced.md#custom-columnfilteroperators)).

Filter values are converted into the column's value type, then parsed by the column type. For example, a column of
`IsoLocalDateType` accepts ISO dates like `2025-01-31`.

The following rules apply:

- Column names and operator symbols are case-sensitive.
- Columns of subtype schemas can be filtered as well.
- Parameters that don't match a column are ignored, so typing errors in column names go unnoticed.
- If an entity filter property has the same name as a column, the parameter is bound to the entity filter property and
  no column filter is applied.
- A column whose filter set has no default operator (`empty`) ignores parameters without an operator.

Invalid column filters are rejected with `400 Bad Request` and one of the following error codes:

| Error code                        | Reason                                                        |
|-----------------------------------|---------------------------------------------------------------|
| `unknown-column-filter-operator`  | The operator is not supported by the column type              |
| `operator-value-ambiguous`        | Several values given for an operator taking exactly one value |
| `operator-not-applicable`         | The operator cannot be applied to the column's value type     |
| `column-filter-conversion-failed` | The value could not be converted into the column's type       |

### Dynamic Fieldsets

Instead of a named fieldset, a request can define the returned fields itself using the `_fields` parameter
(see [Chapter 2: "Dynamic fieldsets"](./02-domain-schema.md#dynamic-fieldsets)). The parameter contains a
comma-separated list of field names; relations can be followed by a fieldset reference after a colon:

```http
GET /TodoItem?_fields=id,title,status
GET /TodoItem?_fields=id,title,list:(id,title)
GET /TodoList/<listId>?_fields=title,items:short
```

See [Chapter 8: "Dynamic Fieldset Syntax"](./08-advanced.md#dynamic-fieldset-syntax) for the complete grammar.

If `_fields` is given and not empty, `_fieldset` is ignored. A syntax error is rejected with `400 Bad Request`
(`parse-dynamic-fieldset`). Dynamic fieldsets are validated like named fieldsets before they are queried: by default,
recursive fieldsets and nesting deeper than three levels are rejected with `400 Bad Request` (`invalid-fieldset`),
configured by `kiwi.fieldset-validation.no-cycles` and `kiwi.fieldset-validation.max-depth`.

### Accelerated responses

With `_accelerate=true`, the response is _accelerated_: nested objects are moved into a `references` list and replaced
by references of the form `{ "@": <index> }`. Equal objects are only included once, which reduces the size of
responses with many repeated relations, like a page of TodoItems that reference the same TodoList:

```http
GET /TodoItem?_fields=id,title,list&_accelerate=true
```

```json
{
  "content": {
    "items": [
      {
        "id": "<itemId1>",
        "title": "Milk",
        "list": {
          "@": 0
        }
      },
      {
        "id": "<itemId2>",
        "title": "Bread",
        "list": {
          "@": 0
        }
      }
    ],
    "pageSize": 50,
    "pageNumber": 0,
    "totalPages": 1,
    "totalItems": 2
  },
  "references": [
    {
      "id": "<listId>",
      "title": "Groceries",
      "author": "alice"
    }
  ],
  "accelerated": true
}
```

The top-level objects (the page items, or the entity of `GET /<entity>/{id}`) stay in place; only objects nested
within them become references. References can be nested: a referenced object can itself contain references. If a
response cannot be accelerated, for example a page of single-column values, `accelerated` is `false` and `content`
contains the regular response.
On the JVM, the `de.kiwi.framework.support` package offers `decelerate()` and `deceleratePage()` extension functions for
`Accelerated` to restore the original structure.

### Request and response bodies

| Endpoint                | Request body                          | Success response                                  |
|-------------------------|---------------------------------------|---------------------------------------------------|
| `GET /<entity>`         | -                                     | `200 OK`, page of entities (`PageResult`)         |
| `GET /<entity>/{id}`    | -                                     | `200 OK`, entity; `404 Not Found` with empty body |
| `POST /<entity>`        | JSON object with the entity data      | `201 Created`, result of the create command       |
| `PATCH /<entity>/{id}`  | JSON object with the fields to update | `200 OK`, result of the update command            |
| `DELETE /<entity>/{id}` | -                                     | `200 OK`, result of the delete command            |
| `GET /<entity>/_schema` | -                                     | `200 OK`, schema metadata                         |

A page of entities has the following format:

```json
{
  "items": [
    {
      "id": "<itemId>",
      "listId": "<listId>",
      "title": "Milk",
      "content": "2 liters",
      "status": "Open",
      "order": 0
    }
  ],
  "pageSize": 50,
  "pageNumber": 0,
  "totalPages": 1,
  "totalItems": 1
}
```

The result of the generic create, update and delete commands is the affected entity, extracted using the command's
`detailsProvider`. The JSON body of `POST` and `PATCH` requests is bound to the body properties declared by the command
(see [Chapter 3: "Generic Commands and Queries"](./03-application-command-query.md#generic-commands-and-queries)).
`PATCH` requests are partial updates: only the fields present in the JSON body are bound, and update handlers using
`patchFields` or `ifPatched` leave absent fields unchanged. Errors are returned as described in
["Exception Handling"](#exception-handling). Invalid, missing and unknown body fields of generic commands are reported
together as `400 Bad Request` with the error code `entity-body-invalid`, whose `errors` argument lists each field error
with its `code` (e.g. `entity-field-invalid`) and `args`.

`GET /<entity>/_schema` returns metadata about the entity, which can be used by generic frontend components: the entity
name and class, the query class and its filter properties, the schema with its fieldsets, columns and relations, and the
create, update and delete command classes with their properties. For generic create and update commands, the
properties are the declared body properties, with their JSON value type and whether they are `required`.

## Analytical Report Endpoints

For each analytical report query
(see [Chapter 4: "Analytical Report Query"](./04-querying-access-control.md#analytical-report-query)), Kiwi registers
two endpoints, using the same entity path names as the generic entity endpoints:

| Method | Path                        | Description                               |
|--------|-----------------------------|-------------------------------------------|
| `GET`  | `/<entity>/_report`         | Query an analytical report                |
| `GET`  | `/<entity>/_report/_schema` | List the available dimensions and metrics |

The report endpoints are disabled with `kiwi.web.report-controller=false`.

The report endpoint accepts the following parameters:

- `_dimensions`: The dimensions to group the report by
- `_metrics`: The metrics to aggregate for each group
- `_sort`: Sort order by dimension or metric names, in the same format as for entity queries; names which are not
  part of the report are ignored
- A parameter named like a dimension defines a **slice** of this dimension: its value is a JSON `Slice` object
  (see [Chapter 4: "Analytical Report Query"](./04-querying-access-control.md#analytical-report-query)). If a slice
  parameter is repeated, all slices are combined with a logical "and".

`_dimensions` and `_metrics` can be repeated or contain comma-separated names. Dimension and metric names are
case-insensitive. Pagination parameters are not used by report queries.

For the `TodoItemReportQuery` of the demo todo-app, the following request reports the number of total, completed and
incomplete items for two specific lists (shown on separate lines for readability; the slice value must be URL-encoded
when sent):

```http
GET /TodoItem/_report
    ?_dimensions=listId
    &_metrics=total,completed,incomplete
    &_sort=total,desc
    &listId={"in":["<listId1>","<listId2>"]}
```

The response contains a header row with the dimension and metric names, and a row for each combination of dimension
values:

```json
{
  "headers": [
    "listId",
    "total",
    "completed",
    "incomplete"
  ],
  "body": [
    [
      "<listId2>",
      12,
      4,
      8
    ],
    [
      "<listId1>",
      3,
      3,
      0
    ]
  ]
}
```

`GET /TodoItem/_report/_schema` returns the available fields with their value types:

```json
{
  "dimensions": [
    {
      "name": "listId",
      "valueType": "..."
    }
  ],
  "metrics": [
    {
      "name": "total",
      "valueType": "..."
    },
    ...
  ]
}
```

Unknown dimensions or metrics are rejected with `400 Bad Request` (`unknown-report-field`), and slice values that
don't match the `Slice` structure with `400 Bad Request` (`entity-mismatched-input`).

## OpenAPI Documentation

Kiwi documents its generated endpoints using [springdoc-openapi](https://springdoc.org/). To enable the documentation,
add springdoc to your application (Kiwi 1.5 is built against springdoc-openapi 3.1):

```kotlin
dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
}
```

The `KiwiOpenApiAutoConfiguration` is active if springdoc is on the classpath, the Kiwi web layer is enabled, and
`kiwi.web.open-api.enabled` is not `false`. It adds to the OpenAPI document:

- All generic entity and report endpoints, using the entity name as path (without the naming-strategy variants)
- Request and response models derived from the entity schemas, and for custom create and update command classes,
  derived from the command classes
- The query parameters of the generated endpoints: pagination, `_fieldset` (with the available fieldset names),
  `_fields`, `_accelerate`, and the filter parameters (see below)
- The error response model (`ErrorBody`) for error responses

The operations of an entity are grouped by the tag `kiwi.<Entity>`, for example `kiwi.TodoItem`. To show your own
controller endpoints in the same group, annotate the controller with `@Tag(name = "kiwi.TodoItem")`, like the demo's
`TodoItemController`. In your own controllers, `UserDescriptor` parameters are hidden from the documentation, and
`EntityId` parameters are documented as strings.

The general API information is declared using springdoc's annotations, for example on the application class:

```kotlin
@SpringBootApplication
@OpenAPIDefinition(
    info = Info(
        version = "1.0",
        title = "Kiwi Todo-App Demo Application",
        description = "Simple Todo-App Server, demonstrating the usage of the Kiwi Framework",
    )
)
class DemoApplication
```

With the springdoc UI starter, the documentation is served at springdoc's default paths `/swagger-ui.html` and
`/v3/api-docs`. Allow access to these paths in your Spring Security configuration as needed.

The OpenAPI integration is configured with the following properties:

```yaml
kiwi:
  web:
    open-api:
      enabled: true
      locale: en
      query-params-details: EntityFiltersOnly
```

`query-params-details` determines which filter parameters are documented for `GET /<entity>`:

| Value                           | Documented filter parameters                                                 |
|---------------------------------|------------------------------------------------------------------------------|
| `NoFilters`                     | None                                                                         |
| `EntityFiltersOnly` (default)   | Entity filter properties                                                     |
| `EntityAndDefaultColumnFilters` | Entity filter properties and `<column>` parameters with the default operator |
| `EntityAndAllColumnFilters`     | Additionally all `<column>:<operator>` parameters                            |

Since every column supports several operators, `EntityAndAllColumnFilters` results in a large number of documented
parameters.

The descriptions of the generated operations and parameters are read from the resource bundle `kiwi-openapi-messages`
in the language given by `kiwi.web.open-api.locale`. Kiwi provides English texts. To use another language, add a
`kiwi-openapi-messages_<language>.properties` file with the same keys to your application resources and set the locale
accordingly.

## Cross-Origin Requests

If a browser frontend is served from another origin than the API, CORS must be configured. You can configure CORS in
your Spring MVC or Spring Security configuration, or let Kiwi register a global CORS mapping for all paths:

```yaml
kiwi:
  web:
    global-cors:
      enabled: true
      allowed-origins: "https://todo.example.com"
      allowed-headers: "*"
      allow-credentials: true
      max-age: 30m
```

At least one allowed origin is required. The allowed methods default to `GET`, `HEAD`, `OPTIONS`, `POST`, `PUT`,
`PATCH` and `DELETE`. When using Spring Security, enable its CORS support (`cors { }`) so that preflight requests are
handled before authentication.

---

Next up, [Chapter 7: "Testing and Frontend Code-Generation"](./07-testing.md) is about unit-, slice- and integration
tests of a Kiwi application, verifying commands and queries, and generating TypeScript fieldset sources for frontends.
