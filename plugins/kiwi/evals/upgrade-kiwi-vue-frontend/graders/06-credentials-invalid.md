---
type: llm
focus: trace
weight: 1
---

`describeLoginError` reads the credentials from `error.credentials` (or no longer reads them), not from `error.details.credentials`.
