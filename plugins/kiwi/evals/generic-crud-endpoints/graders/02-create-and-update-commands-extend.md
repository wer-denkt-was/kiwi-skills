---
type: llm
focus: trace
weight: 1
---

Create and update commands extend `GenericCreate<Project>` and `GenericUpdate<Project, ProjectId>`, override `detailsProvider` with a ProjectFieldsets fieldset, and declare body properties using `by schema(ProjectSchema.…)`.
