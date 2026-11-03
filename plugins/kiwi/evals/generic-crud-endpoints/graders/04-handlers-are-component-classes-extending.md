---
type: llm
focus: trace
weight: 1
---

Handlers are `@Component` classes extending `GenericCreateHandler`, `GenericUpdateHandler` and `GenericDeleteHandler`, and the update handler applies changes with `patchFields`/`ifPatched`.
