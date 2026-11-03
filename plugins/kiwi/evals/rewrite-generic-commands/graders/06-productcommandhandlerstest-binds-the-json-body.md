---
type: llm
focus: trace
weight: 1
---

ProductCommandHandlersTest binds the JSON body with `bind(…, objectMapper)` instead of `jsonBody`, and no longer uses `?: fail(…)` on the handle result.
