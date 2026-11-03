# Kiwi-Vue

&copy; werdenktwas GmbH, 2024–2026 - Licensed under the EUPL-1.2

Vue 3 companion library for the **Kiwi-Framework** backend.
Provides typed API bindings, reactive loading state management, structured error handling, sorting/validation utilities,
and a set of Vue components.

Designed to integrate with a [Kiwi-Framework](https://github.com/wer-denkt-was/kiwi-framework) application server.

| Kiwi-Vue | Kiwi-Server |
|----------|-------------|
| 2.x      | 1.5         |
| 1.x      | 1.4         |

Upgrading from Kiwi-Vue 1.x? See the [changelog](CHANGELOG.md#migrating-from-1x) for the migration guide.

## Installation

```sh
npm install @werdenktwas/kiwi-vue
```

**Peer dependency:** `vue ^3.5`

### Module exports

| Import path                        | Contents                                   |
|------------------------------------|--------------------------------------------|
| `@werdenktwas/kiwi-vue`            | Re-exports all modules as named namespaces |
| `@werdenktwas/kiwi-vue/api`        | Data models and error classes              |
| `@werdenktwas/kiwi-vue/services`   | API client, CRUD, loading, error handler   |
| `@werdenktwas/kiwi-vue/components` | Vue.js utility components                  |
| `@werdenktwas/kiwi-vue/utils`      | Sorting, validation, pagination helpers    |
| `@werdenktwas/kiwi-vue/tooling`    | Vite/unplugin build utilities              |

## API Binding

### Setting up the API client

Call `provideApi` once at the application root (inside a `setup` context). Child components and services access it via
`useApi`. Use `createApi` to create an instance outside of components, e.g. in stores, and pass it to `provideApi`.

```ts
import { provideApi } from '@werdenktwas/kiwi-vue/services'
import { JwtBearerToken } from '@werdenktwas/kiwi-vue/api'

// In a root component setup()
const credentials = ref(new JwtBearerToken(token))

provideApi({
  apiUrl: 'https://api.example.com/', // a trailing slash is appended if missing
  credentials, // MaybeRefOrGetter<ApiCredentials> — reactive
  // fetch: customFetch, // optional fetch implementation
})
```

Request paths are always resolved relative to the API URL; leading slashes are ignored. With the API URL
`https://api.example.com/v1`, the path `/TodoItem` requests `https://api.example.com/v1/TodoItem`. Pass a `URL` to
`api.request(url, init)` to request an absolute URL.

#### Credentials

| Class / value                              | Authentication method                                                              |
|--------------------------------------------|------------------------------------------------------------------------------------|
| `missingCredentials`                       | No authentication                                                                  |
| `new UsernamePassword(username, password)` | HTTP Basic (`Authorization: Basic …`)                                              |
| `new JwtBearerToken(token, leeway?)`       | Bearer token (`Authorization: Bearer …`); validates expiration via JWT `exp` claim |
| `bearerToken(token)`                       | Factory for a bearer-token `AuthenticateRequestFn`                                 |
| `httpBasic(user, pass)`                    | Factory for a Basic-auth `AuthenticateRequestFn`                                   |
| `addRequestHeader(name, value)`            | Add an arbitrary request header                                                    |

Compose multiple authentication functions with `AuthenticateRequestFn.compose(fn1, fn2, ...)`. A credential may also
provide a `rewrite` function to rewrite request URLs (e.g. for proxies).

`JwtBearerToken.isValid()` returns `false` once the token's `exp` claim is in the past (minus an optional leeway in
seconds) — the next request will then be rejected with `CredentialsInvalid`. Tokens without `exp` claim never expire.

> Kiwi-Server 1.5 removed its built-in JWT support: bearer tokens are validated by the application's Spring Security
> configuration, e.g. as an OAuth2 resource server. `JwtBearerToken` works with any such setup, as it only sends the
> token and reads its expiration.

#### Session cookies (OAuth2/OIDC login)

Applications can authenticate using a session cookie instead of a token: The login navigates the browser to the server
(e.g. Spring Security's `oauth2Login`), which performs the OAuth2/OIDC login, establishes a session (e.g. Spring
Session), and redirects back to the application, setting the session cookie.
`SessionCredentials` let the browser send this cookie with each request (`credentials: 'include'`), and send the CSRF
token with XHR/`fetch` requests.

**Session authentication example**:

```ts
import {
  createApi,
  createSession,
  provideApi,
  provideSession,
  unwrapResponseJson,
} from '@werdenktwas/kiwi-vue/services'
import { csrfTokenEndpoint, SessionCredentials } from '@werdenktwas/kiwi-vue/api'

const apiUrl = new URL('https://api.example.com/')
const csrf = csrfTokenEndpoint(new URL('csrf', apiUrl))
const api = createApi({ apiUrl, credentials: new SessionCredentials({ csrf }) })

const session = createSession({
  loadUser: signal => api.request('UserAccount/me', {}, { signal }).then(unwrapResponseJson<IUserAccount>),
  loginUrl: returnUrl => new URL(`login?returnUrl=${encodeURIComponent(returnUrl)}`, apiUrl),
  logoutUrl: new URL('logout', apiUrl),
  csrf,
})

// In the root component setup()
provideApi(api)
provideSession(session)
session.refresh().catch(handleError) // null if not authenticated

// In components
const { user, isAuthenticated, login, logout } = useSession<IUserAccount>()
```

| Option of `SessionCredentials` | Default   | Description                                                                         |
|--------------------------------|-----------|-------------------------------------------------------------------------------------|
| `credentials`                  | `include` | `include` sends cookies to other origins as well, `same-origin` only to the own one |
| `csrf`                         | -         | `CsrfTokenSource`, its token is sent with all requests except `GET`, `HEAD`, etc.   |
| `redirect`                     | `manual`  | `manual` rejects redirects (to a login page) with `UnauthorizedError`               |

Requests without authenticated session reject with an `UnauthorizedError`, either by status `401`, or by a redirect,
e.g. to the login page (status `0`, detail `redirected: true`), as redirects are not followed.

CSRF token sources (`CsrfTokenSource`):

- `csrfTokenEndpoint(url)` requests the token (`{ token, headerName, parameterName }`) from a server endpoint once, when
  it is first needed; `refresh()` requests it again.
- `csrfTokenCookie(options?)` reads the token from the `XSRF-TOKEN` cookie of Spring Security's
  `CookieCsrfTokenRepository.withHttpOnlyFalse()`, sent as `X-XSRF-TOKEN` header. The application can only read the
  cookie if it is set for its domain, e.g. `example.com` for `app.example.com` and `api.example.com`.

The `SessionService` (`createSession`, `provideSession`, `useSession`) tracks the session user as `LoadingState`, which
is `null` if not authenticated. `login(returnUrl?)` navigates to the login URL, `logout()` submits a `POST` form with
the CSRF token to the logout URL (or navigates to it with `logoutMethod: 'GET'`). Call `refresh(false)` periodically to
detect an expired session without switching to the pending state.

Use `extendCredentials(credentials, ...fns)` to add request headers to credentials, keeping their CSRF handling:
`extendCredentials(new SessionCredentials({ csrf }), addRequestHeader('X-Tenant-Id', tenantId))`. Credentials can
implement `prepare(init)` to load data asynchronously before a request is authenticated, as `SessionCredentials` does
for the CSRF token.

**Server requirements**:

- **CORS** with credentials if the application is served from another origin, e.g. `kiwi.web.global-cors` with
  `allowed-origins` and `allow-credentials: true`, and `cors { }` in the Spring Security filter chain.
- **Cookies**: Browsers only send cookies with `SameSite=Lax` (the default of Spring Session) to the same site, e.g.
  from `app.example.com` to `api.example.com`. Other sites require `SameSite=None; Secure`, which browsers blocking
  third-party cookies don't send either. Prefer serving application and API from the same site.
- **CSRF token endpoint** for `csrfTokenEndpoint`, permitted for all requests:
  ```kotlin
  @RestController
  class CsrfController {
      @GetMapping("/csrf")
      fun csrf(token: CsrfToken): CsrfToken = token
  }
  ```
- **Logout** via `POST` with CSRF token (Spring Security's default), or via `GET` if configured so.

### CRUD services with `basicCrudService`

`basicCrudService<T, ID, C, U>(entityType)` generates a class that implements `CrudService<T, ID, C, U>` against the
generic entity endpoints of Kiwi-Server. The type parameters are the entity (fieldset) type `T`, its ID type `ID`, and
the JSON body types of create (`C`) and update (`U`) commands. The TypeScript sources generated by Kiwi-Server's
`TypescriptFieldsetSourceRenderer` provide all of them:

```ts
import { basicCrudService, useApiService } from '@werdenktwas/kiwi-vue/services'
import type { ICreateTodoItem, ITodoItemDefault, IUpdateTodoItem, TodoItemId } from './demo.model'

class TodoItemService extends basicCrudService<ITodoItemDefault, TodoItemId, ICreateTodoItem, IUpdateTodoItem>(
  'TodoItem',
) {
}

// In a component setup()
const todoItemService = useApiService(TodoItemService)
await todoItemService.update(id, { title: 'Bread' }) // PATCH: only "title" is changed
```

| Method                      | HTTP call                | Description                         |
|-----------------------------|--------------------------|-------------------------------------|
| `findById(id, options?)`    | `GET /TodoItem/{id}`     | Returns `null` for 404              |
| `requireById(id, options?)` | `GET /TodoItem/{id}`     | Throws `EntityNotFound` for 404     |
| `findPage(options?)`        | `GET /TodoItem?{params}` | Returns `IPage<T>`                  |
| `create(body, options?)`    | `POST /TodoItem`         | Returns created entity              |
| `update(id, body, opts?)`   | `PATCH /TodoItem/{id}`   | Partial update, returns entity      |
| `delete(id, options?)`      | `DELETE /TodoItem/{id}`  | Returns deleted entity              |
| `fetchMetadata(options?)`   | `GET /TodoItem/_schema`  | Returns `GenericMetadata` of entity |

IDs are URL-encoded in paths. Updates are partial (`PATCH`): Kiwi-Server only changes the properties present in the
body, and rejects unknown properties, so only send the properties declared by the update command.

`fetchMetadata()` returns the entity's `GenericMetadata`: its fieldsets, columns, relations, query filter properties,
and
the body properties of create and update commands including whether they are `required`.

#### Options

```ts
interface RequestOptions {
  signal?: AbortSignal // abort the request
}

interface FindOptions extends RequestOptions {
  fieldset?: string
  fields?: string
  accelerate?: boolean
}

// extends FindOptions + Pagination
interface FindPageOptions {
  pageSize?: number
  pageNumber?: number
  filter?: Record<string, ParamValues> // entity filter properties and column filters
  sort?: Sort[] // [{ field: 'name', direction: 'asc' }]
}
```

#### Column filters

Kiwi-Server filters entities by columns of the entity schema using parameters `<column>=<values>` (default operator,
`in` for built-in column types) or `<column>:<operator>=<values>`. `columnFilter(column, operator, values)` (from
`api`) creates such a parameter, escaping commas and backslashes within values:

```ts
import { columnFilter } from '@werdenktwas/kiwi-vue/api'

await todoItemService.findPage({
  filter: {
    ...columnFilter('status', undefined, ['Open', 'Done']), // status=Open,Done
    ...columnFilter('title', 'contains', 'Milk, 2l'), // title:contains=Milk\, 2l
    ...columnFilter('order', 'ge', 1), // order:ge=1
  },
})
```

Built-in operators: `in`, `not`, `eq`, `ne`, `null`, `gt`, `ge`, `lt`, `le`, `contains`, `startsWith`.

The `accelerate` option requests a compact response format from the server (shared-reference encoding). The client
decodes it transparently.

#### Pagination model

```ts
interface IPage<T> {
  items: ReadonlyArray<T>
  pageSize: number
  pageNumber: number
  totalPages: number
  totalItems: number
}
```

Use `loadAllPages({ loadPage, pageSize?, sort? })` (from `utils`) to fetch all pages and merge them into a single array,
see [Other utilities](#other-utilities).
Use `sortParams(sort)` (from `api`) to format a sort order as `_sort` parameter values in custom services.

### Analytical report services

For analytics endpoints that support the Kiwi report query format:

```ts
import { basicReportService, useApiService } from '@werdenktwas/kiwi-vue/services'

class SalesReportService extends basicReportService('sales') {
}

const reportService = useApiService(SalesReportService)
const report = await reportService.queryReport({
  dimensions: ['region', 'month'],
  metrics: ['revenue', 'units'],
  slices: { region: { eq: 'EU' } },
  sort: [{ field: 'month', direction: 'asc' }],
})
```

`reportService.fetchReportSchema()` loads the available dimensions and metrics (`GET /sales/_report/_schema`).

The returned `AnalyticalReport` object provides methods for data transformation:

| Method                            | Description                                               |
|-----------------------------------|-----------------------------------------------------------|
| `columnArray(header)`             | Extract a single column as an array                       |
| `columnSum(header)`               | Sum all values in a numeric column                        |
| `projection(columns)`             | Select and/or transform columns                           |
| `extendColumns(columns)`          | Add computed columns alongside existing ones              |
| `groupBy(header)`                 | Returns a `Map` keyed by column value                     |
| `pivot(header)`                   | Returns `[key, AnalyticalReport][]` sub-reports per group |
| `aggregate(header, aggregations)` | Reduce groups to aggregated values                        |
| `collate()`                       | Convert rows to `Record<string, unknown>[]`               |

Slice predicates: `eq`, `gt`, `lt`, `ge`, `le`, `in`, `null`, plus `and`, `or`, `not` for compound expressions.

## Error Handling

### Provider and injection

Set up an `ApiErrorHandler` at or near your application root:

```ts
import { provideApiErrorHandler } from '@werdenktwas/kiwi-vue/services'

provideApiErrorHandler({
  transform: myTransformer, // ApiErrorTransformer
  consumers: [myConsumer], // Array<ApiErrorConsumer>
  inherit: true, // forward to parent handler as well
})
```

Inject in any child component:

```ts
import { useApiErrorHandler } from '@werdenktwas/kiwi-vue/services'

const errorHandler = useApiErrorHandler()
```

If no provider is found, a fallback handler is used that logs a warning to the console.

#### `ApiErrorHandler` interface

```ts
errorHandler.handle(error) // transform + dispatch to consumers
errorHandler.catch(promise) // attach .catch() that calls handle(), rethrows
errorHandler.format(error) // returns formattedMessage string only
errorHandler.wrapService(service) // returns Proxy auto-handling all Promise rejections
```

`wrapService` is the recommended pattern for services — it wraps every method so that any rejected promise is
automatically routed through the handler without manual `.catch` calls at every call site:

```ts
const rawService = useApiService(UserService)
const userService = errorHandler.wrapService(rawService)

// Error from this call will be handled automatically before being rethrown
userService.findPage()
```

### API errors

Failed requests reject with an `ApiError`, created from the Kiwi-Server error response body
(`{ message, arguments, errorType }`):

| Property    | Description                                                                 |
|-------------|-----------------------------------------------------------------------------|
| `code`      | Error code (`message` of the response), e.g. `entity-not-found`             |
| `details`   | Error details (`arguments` of the response)                                 |
| `status`    | HTTP status code of the response                                            |
| `errorType` | Type of the error, usually the exception class name                         |
| `errors`    | Nested errors, e.g. one per invalid field of an `entity-body-invalid` error |

Responses with status `401` (and redirects of requests not following redirects, see
[Session cookies](#session-cookies-oauth2oidc-login)) reject with an `UnauthorizedError`, `403` with a `ForbiddenError`.
Their code is taken from the response body if present (e.g. `access-denied`), and is `unauthorized` or `forbidden`
otherwise, e.g. if Spring Security rejected the request without a body. Other responses without error body result in the
code `http-error` with the detail `status`. Successful responses whose body is not valid JSON (e.g. an HTML page of a
misconfigured proxy or development server) reject with the code `response-not-json`, with the details `status` and
`contentType`.

Kiwi-Server reports all invalid, missing and unknown body properties of generic commands together as
`entity-body-invalid`:

```ts
try {
  await todoItemService.create({ list, title: '' })
} catch (error) {
  if (error instanceof ApiError && error.code === 'entity-body-invalid') {
    error.errors.forEach(fieldError => console.log(fieldError.code, fieldError.details.fieldName))
  }
}
```

### Error transformers

A transformer is a function `(error: unknown) => ApiErrorDetails`:

```ts
interface ApiErrorDetails {
  error: unknown
  rawMessage: string // technical message
  formattedMessage: string // user-facing message
  causes?: ApiErrorDetails[] // transformed nested errors, see ApiError.errors
}
```

The built-in transformers transform nested errors into `causes`. If no message is found for an error with nested errors
(e.g. `entity-body-invalid`), its `formattedMessage` consists of the formatted messages of its causes, one per line.
Use `apiErrorTransformer(formatApiError)` to create a transformer with the same behavior from a function formatting
`ApiError`s.

#### `simpleApiErrorTransformer`

Uses `error.message` (or a JSON/string representation) as both raw and formatted message. Good as a fallback.

#### `i18nErrorTransformer` — vue-i18n integration

Maps `ApiError` codes to translated messages using a vue-i18n translation namespace:

```ts
import { i18nErrorTransformer } from '@werdenktwas/kiwi-vue/services'
import { useI18n } from 'vue-i18n'

const { tm, rt } = useI18n()
const transform = i18nErrorTransformer({ tm, rt, messagesKey: 'errors' })
```

Translation keys in your i18n messages file:

```json
{
  "errors": {
    "entity-not-found": "The resource could not be found.",
    "entity-not-found[entityType=de.kiwi.demo.domain.TodoItem]": "Todo item '{id}' does not exist.",
    "entity-field-missing-value[fieldName=title]": "Please enter a title.",
    "forbidden": "Access denied.",
    "access-denied": "Access denied."
  }
}
```

Key format: `"error-code"` for a generic match, or `"error-code[conditions]"` for a conditional match on
`ApiError.details`. Conditions are separated by `,`, each is either `key=value` (the detail `key` equals `value`) or
`key` (the detail `key` is present). Codes and values are compared case-insensitively. The most specific (most
conditions) matching key wins. Detail values are passed to `rt` as named arguments, i.e. `{argumentName}` placeholders
in vue-i18n. Note that Kiwi-Server reports `entityType` as fully qualified class name.

#### `resourceErrorTransformer` — JSON resource objects

A plain-object alternative to i18n, suitable when internationalization is not required:

```ts
import { resourceErrorTransformer } from '@werdenktwas/kiwi-vue/services'

const transform = resourceErrorTransformer({
  'entity-not-found': 'The requested resource was not found.',
  'entity-not-found[entityType=user]': "User '{{id}}' does not exist.",
  forbidden: 'You do not have permission to perform this action.',
})
```

The same key format applies as with `i18nErrorTransformer`, detail values are inserted for `{{argumentName}}`
placeholders. Multiple resource objects may be passed; they are merged, later resources take precedence.

### Error consumers

A consumer is a function `(error: ApiErrorDetails) => void`. The built-in `consoleErrorConsumer` calls `console.error`.
Typical use is displaying a toast notification:

```ts
import { consoleErrorConsumer } from '@werdenktwas/kiwi-vue/services'
import type { ApiErrorConsumer } from '@werdenktwas/kiwi-vue/services'

const toastConsumer: ApiErrorConsumer = ({ formattedMessage }) => {
  toast.error(formattedMessage)
}

provideApiErrorHandler({
  transform: myTransformer,
  consumers: [toastConsumer, consoleErrorConsumer],
})
```

## Loading Service

`useLoading<T>()` creates a reactive loading service that manages async operations and exposes their state.

```ts
import { useLoading } from '@werdenktwas/kiwi-vue/services'

const users = useLoading<User[]>()

async function refresh() {
  await users.load(userService.findPage())
}
```

### `LoadingState<T>` variants

| State     | Shape                                        | Description            |
|-----------|----------------------------------------------|------------------------|
| `idle`    | `{ loading: 'idle' }`                        | Not yet started        |
| `pending` | `{ loading: 'pending', previousResult?: T }` | Operation in progress  |
| `loaded`  | `{ loading: 'loaded', result: T }`           | Completed successfully |
| `failed`  | `{ loading: 'failed', error: unknown }`      | Completed with error   |

When a new `load` is started while another is pending, the previous operation's result is silently discarded — only the
latest operation updates the state. Upon starting a new load operation (or calling `reset()`), the previous
`AbortController` is aborted if one was provided. Pass its signal to the request to cancel it:

```ts
const abort = new AbortController()
await users.load(userService.findPage({ signal: abort.signal }), { abort })
```

### `load` options

```ts
// Second argument: boolean (pending = true/false) or LoadingOptions
await users.load(promise, { pending: true, abort: abortController })
```

- `pending` (default `true`): switch to `pending` state immediately. Pass `false` to stay in the current state until
  resolved — useful for background refresh without clearing the view.
- `abort`: an `AbortController` that is signalled if a newer `load` call supersedes this one, allowing the underlying
  request to be cancelled.

### Reactive helpers

```ts
users.state // ShallowRef<LoadingState<T>>
users.isLoading // ComputedRef<boolean>
users.resultOrNull // ComputedRef<null | T>

users.replace(r => [...r, newItem]) // update loaded result in-place
users.emplace(cachedUsers) // set loaded result, ignoring a pending operation
users.reset() // back to idle, ignoring a pending operation

users.computeState(r => r.length) // ComputedRef<LoadingState<number>>, maps previousResult too
users.computeResult(r => r.length, 0) // ComputedRef<number>, pending → 0
users.resultOrElse([] as User[]) // ComputedRef<User[]>, pending → []

users.combineStates(otherState, (a, b) => ({ a, b })) // merge two loading states
```

## Utilities

### Sorting

```ts
import { Compare, sorting, Ordinal } from '@werdenktwas/kiwi-vue/utils'
```

`Compare<T>` is a comparator function `(a: T, b: T) => Ordinal` (values: `Ordinal.LT = -1`, `Ordinal.EQ = 0`,
`Ordinal.GT = +1`).

`sorting(compare?)` returns a function `(array: ReadonlyArray<T>) => T[]` returning a sorted copy — useful as a pipeline
step. Unlike `Array.prototype.sort`, it passes `undefined` items to the comparator as well (e.g. for
`Compare.nullsFirst`).

```ts
const byName = Compare.byKey('name', Compare.caseInsensitive)
const sortUsers = sorting(Compare.compose(byName, Compare.byKey('id')))
const sorted = sortUsers(users)
```

| Comparator                    | Description                            |
|-------------------------------|----------------------------------------|
| `Compare.natural`             | JS `>` / `<` operators                 |
| `Compare.number`              | Sign of `a - b`                        |
| `Compare.caseInsensitive`     | `localeCompare` with base sensitivity  |
| `Compare.reverse(cmp?)`       | Reverse order                          |
| `Compare.byKey(key, cmp?)`    | Compare by object property             |
| `Compare.compareBy(fn, cmp?)` | Compare by computed value              |
| `Compare.compose(...cmps)`    | Chain comparators, first non-zero wins |
| `Compare.nullsFirst(cmp?)`    | Nullish values sort first              |
| `Compare.nullsLast(cmp?)`     | Nullish values sort last               |

### Validation

```ts
import { isNotEmpty, requireLength, validateEvery } from '@werdenktwas/kiwi-vue/utils'
```

A `ValidationRule<T>` is `(value: undefined | T) => boolean | string` — `true` means valid; `false` or a string means
invalid (string is the error reason).

A `Validation<T>` is a factory: `(message?: string | false) => ValidationRule<T>`.

```ts
const nameRule = validateEvery([isNotBlank('Name must not be blank'), requireLength({ max: 100 })('Name is too long')])
```

#### Built-in validations

| Validation                       | Checks                            |
|----------------------------------|-----------------------------------|
| `isNotEmpty`                     | Value is truthy                   |
| `isNotBlank`                     | String has non-whitespace content |
| `isPositive`                     | Number > 0                        |
| `isNegative`                     | Number < 0                        |
| `requireLength({ min?, max? })`  | String length in range            |
| `requireBetween({ min?, max? })` | Number in range, or `undefined`   |
| `requireMatches(regex)`          | String matches pattern            |
| `isValidEmail`                   | Basic e-mail format               |
| `isLikeInt`                      | Integer format, e.g. `-12`        |
| `isLikeFloat`                    | Parseable as float                |

#### Conditional and composite

```ts
validateIf(predicate, rule) // apply rule only when predicate is true
validateIfNotBlank(rule) // apply rule only when string is not blank
validateIfNotEmpty(rule) // apply rule only when string is not empty
validateOptional(rule) // apply rule only when value is truthy
validateAsInt(message, numRule) // require integer format (see isLikeInt), then apply numeric rule
validateEvery([...rules]) // AND — fails on first failure
validateSome([...rules]) // OR — succeeds if any rule succeeds
```

### Other utilities

```ts
import { loadAllPages } from '@werdenktwas/kiwi-vue/utils'

// Load all pages of a paginated endpoint into a flat array
const allUsers = await loadAllPages({
  loadPage: pagination => userService.findPage({ ...pagination }),
  pageSize: 200,
  sort: { field: 'id', direction: 'asc' }, // or an array of sort orders
})
```

The first page is loaded to determine the number of pages, the remaining pages are loaded concurrently. Use a sort order
that is unique per item (the default sorts by `id`), so that no item appears on several pages.

```ts
import { deferPromise } from '@werdenktwas/kiwi-vue/utils'

const deferred = deferPromise<string>()
deferred.resolve('done')
await deferred // 'done'
```

```ts
import { delay } from '@werdenktwas/kiwi-vue/utils'

const result = await delay(500, fetch('/api/data'))
```

```ts
import { TODO, unreachable } from '@werdenktwas/kiwi-vue/utils'

// Throws at runtime, signals incomplete implementation to the compiler
const value = TODO('implement this branch')

// Exhaustive switch guard — TypeScript error if a case is missing
switch (
  ...)
  {
  case ...:
    break;
  default:
    unreachable(state)
  }
```

## Vue Components

### `<Loading>`

Renders one of four named slots based on a `LoadingState<T>` or `LoadingService<T>`.

```html

<Loading :state="users">
  <template #idle>
    <p>Press "Load" to fetch users.</p>
  </template>
  <template #pending="{ previousData }">
    <UserList v-if="previousData" :users="previousData" class="stale" />
    <Spinner v-else />
  </template>
  <template #loaded="{ data }">
    <UserList :users="data" />
  </template>
  <template #failed="{ error }">
    <ErrorMessage :error="error" />
  </template>
</Loading>
```

| Prop    | Type                                   | Description     |
|---------|----------------------------------------|-----------------|
| `state` | `LoadingState<T> \| LoadingService<T>` | State to render |

| Slot       | Bindings                 | Shown when              |
|------------|--------------------------|-------------------------|
| `#idle`    | —                        | `loading === 'idle'`    |
| `#pending` | `{ previousData?: T }`   | `loading === 'pending'` |
| `#loaded`  | `{ data: T }`            | `loading === 'loaded'`  |
| `#failed`  | `{ error: unknown }`     | `loading === 'failed'`  |
| `#unknown` | `{ unreachable: never }` | Exhaustiveness guard    |

The `#pending` slot receives `previousData` when the service has a result from a prior load — useful for showing stale
data while refreshing.

Passing a `LoadingService<T>` directly (instead of `LoadingState<T>`) is equivalent to passing `service.state.value`.

### `<ErrorBoundary>`

Catches errors thrown by child component trees using Vue's `onErrorCaptured` hook and renders a fallback `#error` slot.

```vue
<ErrorBoundary stop-propagation @error-caught="logError">
  <template #default>
    <UserProfile :id="userId" />
  </template>
  <template #error="{ error, info, resetError }">
    <div class="error-panel">
      <p>Something went wrong: {{ error.message }}</p>
      <button @click="resetError">Try again</button>
    </div>
  </template>
</ErrorBoundary>
```

| Prop              | Type                | Description                                                        |
|-------------------|---------------------|--------------------------------------------------------------------|
| `stopPropagation` | `boolean`           | Prevent error from propagating to parent `ErrorBoundary` instances |
| `consoleLog`      | `'warn' \| 'error'` | Log caught errors to the browser console                           |

| Event          | Payload                 | Fired when                                      |
|----------------|-------------------------|-------------------------------------------------|
| `error-caught` | `error: Error`          | An error is captured from the child tree        |
| `error-reset`  | `previousError?: Error` | `resetError()` is called from the `#error` slot |

| Slot       | Bindings                                                 | Shown when                 |
|------------|----------------------------------------------------------|----------------------------|
| `#default` | —                                                        | No error is active         |
| `#error`   | `{ error: Error, info: string, resetError: () => void }` | An error has been captured |

If no `#error` slot is provided, errors are captured but the `#default` slot continues to render.

## Vite Tooling

### `KiwiResolver`

Integrates with [`unplugin-vue-components`](https://github.com/unplugin/unplugin-vue-components) to enable automatic
component imports. With this resolver in place, `<Loading>` and `<ErrorBoundary>` can be used in templates without
explicit `import` statements.

```ts
// vite.config.ts
import { defineConfig } from 'vite'
import Vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { KiwiResolver } from '@werdenktwas/kiwi-vue/tooling'

export default defineConfig({
  plugins: [
    Vue(),
    Components({
      resolvers: [KiwiResolver()],
    }),
  ],
})
```

The resolver maps component names to the `@werdenktwas/kiwi-vue/components` entry point. TypeScript types for global
components are provided automatically via the `components/index.ts` module augmentation — add it to your `tsconfig.json`
includes if global type inference is needed.

## Development

```sh
npm install
npm test         # Run unit tests (Vitest)
npm run coverage # Run unit tests with coverage report (coverage/)
npm run build    # Type-check and build the library into dist/
npm run lint     # Lint and fix sources (ESLint)
npm run format   # Format sources (Prettier)
```
