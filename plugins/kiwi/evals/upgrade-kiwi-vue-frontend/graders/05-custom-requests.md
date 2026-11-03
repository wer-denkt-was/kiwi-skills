---
type: llm
focus: trace
weight: 1
---

The health check in src/stores/session.ts still requests `/actuator/health` at the root of the backend origin, using an absolute URL (e.g. `new URL('/actuator/health', …)`), and the `FetchApi` construction follows the 2.0 API (`createApi(...)`, or `new FetchApi(baseUrl, fetch, preprocessor)`).
