# Rewriting generic commands from Kiwi 1.4 to 1.5

In Kiwi 1.4, generic create and update commands carried the raw JSON request body (`jsonBody`), and handlers read
values through the `CreateBody`/`UpdateBody` wrappers by schema field. In Kiwi 1.5, the commands **declare their body
properties** as Kotlin properties, the body is bound and validated once (`bind(json, objectMapper)`), and handlers
receive the typed command. The declared properties also define the OpenAPI documentation, the `_schema` metadata and
the generated frontend fieldsets, so they are the contract of the endpoint.

Full reference: chapter 3, "Generic Commands and Queries" (`../../kiwi-server/references/03-application-command-query.md`).

## Contents

1. Mapping table
2. Commands
3. Handlers
4. JSON keys (breaking for clients)
5. Tests
6. Checklist per entity

## 1. Mapping table

| Kiwi 1.4                                                     | Kiwi 1.5                                                                  |
|--------------------------------------------------------------|---------------------------------------------------------------------------|
| `override val entityFieldset = XFieldsets.default`           | `override val detailsProvider = XFieldsets.default`                       |
| `override fun entityDetails(entity, user)`                   | A custom `JsonDetailsProvider<E>` as `detailsProvider`                    |
| `Fieldset.jsonDetails`                                       | The fieldset itself (fieldsets are `JsonDetailsProvider`s)                |
| `override fun entityId(entity) = entity.id` (GenericCreate)  | Remove: defaults to `BaseEntity.id`; keep for entities without BaseEntity |
| `lateinit var jsonBody: JsonNode` / `command.jsonBody = …`   | `command.bind(json, objectMapper)`                                        |
| `createEntity(create: CreateBody, user)`                     | `createEntity(create: CreateX, user)`                                     |
| `afterEntityCreated(created, create: CreateBody)`            | `afterEntityCreated(created, create: CreateX, user)`                      |
| `updateEntity(update: UpdateBody, entity, user)`             | `updateEntity(update: UpdateX, entity, user)`                             |
| `create.require(Schema.column)` / `create[Schema.column]`    | `create.column` (declared `by schema(Schema.column)`)                     |
| `create.require<T>("key")` / `create.get<T>("key")`          | `create.key` (declared `by property<T>()`)                                |
| `create.require(Schema.parentId)` + `requireById`            | `create.parent` (declared `by reference(Schema.parent)`) + `requireById`  |
| `update.patchField(Schema.a)`; `update.patchField(Schema.b)` | `update.patchFields(entity, update::a, update::b)`                        |
| `update.patch(Schema.x) { valueOrNull -> … }`                | `update.ifPatched(update::x) { valueOrNull -> … }`                        |
| `update.patchNotNull(Schema.x) { value -> … }`               | `update.ifPatchedNotNull(update::x) { value -> … }`                       |
| `update[Schema.x] != null` / presence checks                 | `update.isPatched(update::x)`                                             |
| All schema columns patched                                   | `update.patchFields(entity, …)` listing every writable property           |
| `handler.handle(command, user) ?: fail(…)` in tests          | `handle` returns a non-null `DomainResult`                                |

Property delegates of `GenericCreate`/`GenericUpdate`:

- `by schema(Schema.column)`: value of a `ColumnField`, parsed by its column type, with the column's auto
  validation. JSON key = schema field name. Subtype columns are allowed. Read-only columns (primary key, `val`
  properties, `parentEntityId`) are allowed for reading the value in the handler, but must not be passed to
  `patchFields`: that fails with `GenericCommandMisconfigured` (`500`) on every request. Entity type columns need
  `subtype(...)` (startup fails otherwise).
- `by property<T>()` / `by property<T>("key")`: any Jackson-deserializable value that is not part of the schema.
- `by reference(Schema.toOne)`: the `EntityId` of the referenced entity (typed ID). The handler loads the entity.
- `by references(Schema.toMany)`: a list of `EntityId`s.
- `by subtype(Schema.type)`: selects the entity class to create from an `entityType()`/`entityDiscriminator()` column.
- Modifiers: `.optional()` (nullable type → optional key), `.default(value)` (create only), `.validate(validation)`.

Requiredness follows the Kotlin type in create commands: non-null → required key, nullable → optional. In update
commands every key is optional (patch semantics), and reading an absent property fails, so always go through
`isPatched`/`ifPatched`/`patchFields`.

## 2. Commands

Before (1.4):

```kotlin
class CreateTodoItem : GenericCreate<TodoItem>() {
    override fun entityId(entity: TodoItem) = entity.id
    override val entityFieldset = TodoItemFieldsets.default
    override fun preAuthorize(user: UserDescriptor) = Unit
}

class UpdateTodoItem : GenericUpdate<TodoItem, TodoItemId>() {
    override val entityFieldset = TodoItemFieldsets.default
    override fun preAuthorize(user: UserDescriptor) = Unit
}

class DeleteTodoItem : GenericDelete<TodoItem, TodoItemId>() {
    override val entityFieldset = TodoItemFieldsets.default
    override fun preAuthorize(user: UserDescriptor) = Unit
}
```

After (1.5):

```kotlin
class CreateTodoItem : GenericCreate<TodoItem>() {
    override val detailsProvider = TodoItemFieldsets.default
    val list by reference(TodoItemSchema.list)      // JSON key "list" (was "listId", see section 4)
    val title by schema(TodoItemSchema.title)
    val content by schema(TodoItemSchema.content)
    override fun preAuthorize(user: UserDescriptor) = Unit
}

class UpdateTodoItem : GenericUpdate<TodoItem, TodoItemId>() {
    override val detailsProvider = TodoItemFieldsets.default
    val title by schema(TodoItemSchema.title)
    val content by schema(TodoItemSchema.content)
    val status by schema(TodoItemSchema.status)
    val order by schema(TodoItemSchema.order)
    override fun preAuthorize(user: UserDescriptor) = Unit
}

class DeleteTodoItem : GenericDelete<TodoItem, TodoItemId>() {
    override val detailsProvider = TodoItemFieldsets.default
    override fun preAuthorize(user: UserDescriptor) = Unit
}
```

The 1.4 command did not declare its body, so **derive the properties from the handler**: every `create.require(…)`,
`create[…]`, `update.patchField(…)`, `update.patch…(…)` call names a key the endpoint accepts. Keys that the 1.4
handler read but that are not schema columns become `property<T>()` properties. Keep the authorization logic
(`preAuthorize`) unchanged.

Since Kiwi 1.5 rejects unknown keys, a key that clients send but the 1.4 handler silently ignored now causes
`400 entity-body-invalid`. If clients are known to send such keys, list them in the upgrade report instead of
inventing properties for them.

## 3. Handlers

Before (1.4):

```kotlin
@Component
class CreateTodoItemHandler(
    private val listRepository: TodoListRepository,
) : GenericCreateHandler<CreateTodoItem, TodoItem>() {
    override fun createEntity(create: CreateBody, user: UserDescriptor) = DomainResult { record ->
        val list = listRepository.requireById(create.require(TodoItemSchema.listId))
        val title = create.require(TodoItemSchema.title)
        val content = create.require(TodoItemSchema.content)
        user.requireAdminOrListAuthor(list)
        record(TodoItem.create(list, title = title, content = content))
            .also { item -> list.addItem(item) }
    }
}

@Component
class UpdateTodoItemHandler : GenericUpdateHandler<UpdateTodoItem, TodoItem>() {
    override fun updateEntity(update: UpdateBody, entity: TodoItem, user: UserDescriptor) = DomainResult { record ->
        user.requireAdminOrListAuthor(entity.todoList)
        update.patchField(TodoItemSchema.title)
        update.patchField(TodoItemSchema.content)
        update.patchField(TodoItemSchema.status)
        update.patchNotNull(TodoItemSchema.order) { order -> record(entity.moveOrderInList(order)) }
    }
}
```

After (1.5):

```kotlin
@Component
class CreateTodoItemHandler(
    private val listRepository: TodoListRepository,
) : GenericCreateHandler<CreateTodoItem, TodoItem>() {
    override fun createEntity(create: CreateTodoItem, user: UserDescriptor) = DomainResult { record ->
        val list = listRepository.requireById(create.list)
        user.requireAdminOrListAuthor(list)
        record(TodoItem.create(list, title = create.title, content = create.content))
            .also { item -> list.addItem(item) }
    }
}

@Component
class UpdateTodoItemHandler : GenericUpdateHandler<UpdateTodoItem, TodoItem>() {
    override fun updateEntity(update: UpdateTodoItem, entity: TodoItem, user: UserDescriptor) = DomainResult { record ->
        user.requireAdminOrListAuthor(entity.todoList)
        update.patchFields(entity, update::title, update::content, update::status)
        update.ifPatchedNotNull(update::order) { order -> record(entity.moveOrderInList(order)) }
    }
}
```

Notes:

- `patchNotNull` (1.4) ignored explicit `null` values; map it to `ifPatchedNotNull`. `patch` (1.4) passed `null`
  values on; map it to `ifPatched`. The demo app uses `ifPatched` for `order`, which is equivalent there because
  the column is not nullable (a JSON `null` is rejected when binding).
- **Client-visible:** for a non-nullable column, a JSON `null` is rejected when binding (`400 entity-body-invalid`),
  before the handler runs. A client that sent `null` to a 1.4 `patchNotNull` field (ignored silently) fails now.
  Record these keys in the upgrade report under the client-facing changes; for nullable columns nothing changes.
- `patchFields` writes values to the entity property of the column. If the 1.4 handler used a setter method or domain
  method instead of `patchField`, keep that call inside `ifPatched`.
- Validation moved from the handler into binding: column auto validation runs when binding `schema(…)` properties.
  Remove duplicate manual checks only if they are identical; keep business validation.
- `GenericDeleteHandler.beforeDeleteEntity(delete, entity, user)` is unchanged apart from the command's
  `detailsProvider`.
- `persistEntity` is overridable now; `PersistableBaseEntity.markAsNew()` entities are inserted without a `SELECT`.

## 4. JSON keys (breaking for clients)

The JSON key of a `schema(…)`/`reference(…)`/`subtype(…)` property is the **schema field name**, not the Kotlin
property name. In 1.4, handlers could read any key, typically the name of a parent-ID column such as
`TodoItemSchema.listId` (key `"listId"`). With `reference(TodoItemSchema.list)` the key becomes `"list"`.

- An ID column of the relation (like `listId by parentEntityId(...)`) can be declared using `schema(Schema.listId)`:
  it keeps the key `"listId"` and the typed ID, but is read-only, so the handler loads the entity itself and must not
  pass it to `patchFields`. Decide between `reference(Schema.list)` (typed reference, key changes to `"list"`, clients
  must adapt, documented as a reference) and `schema(Schema.listId)` (keeps the key). Prefer `reference` unless the
  clients can't be changed together with the server, and ask the user if unsure.
- Record every changed key in the upgrade report: frontends and API clients must be updated together with the server.
- A warning is logged at startup when the Kotlin property name differs from the JSON key.

## 5. Tests

```kotlin
// 1.4
val command = UpdateTodoItem().apply {
    entityId = item.id
    jsonBody = objectMapper.readTree("""{ "title": "new" }""")
}
val events = tx { handler.handle(command, user) } ?: fail("missing events")

// 1.5
val command = UpdateTodoItem().apply {
    entityId = item.id
    bind(objectMapper.readTree("""{ "title": "new" }"""), objectMapper)
}
val events = tx { handler.handle(command, user) }
```

The `objectMapper` is a Jackson 3 `tools.jackson.databind.ObjectMapper`; tests without a Spring context need the
`EntityIdJsonModule`: `jacksonMapperBuilder().addModule(EntityIdJsonModule()).build()`.

Tests that send HTTP requests to generic endpoints change `put(...)` to `patch(...)`, and invalid bodies now respond
with `400` and the code `entity-body-invalid`, listing the field errors in `errors`.

## 6. Checklist per entity

1. List the keys the 1.4 handler reads (and the keys clients send, if known).
2. Declare them on the create/update commands; replace `entityFieldset` by `detailsProvider` on all three commands.
3. Change the handler signatures to the typed commands and replace body accessors by property reads/patch methods.
4. Update tests: `bind(...)`, no `?: fail(...)`, `PATCH` instead of `PUT`.
5. Compile and run the entity's tests before moving to the next entity.
6. Note changed JSON keys, newly rejected keys and newly rejected `null` values in the upgrade report.
