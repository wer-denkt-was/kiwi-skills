---
type: llm
focus: trace
weight: 1
---

UpdateProductHandler applies name and description using `patchFields` (or `ifPatched`), and price and archived using `ifPatchedNotNull`, still recording the events returned by `changePrice` and `archive`.
