# Kiwi skills for Claude Code

This repository contains the [Claude Code](https://code.claude.com) plugin `kiwi` for developing applications with
the Kiwi Server Framework and Kiwi-Vue, published by the marketplace `kiwi-framework` declared in
`.claude-plugin/marketplace.json`. The plugin contains three skills:

| Skill          | Invocation                                          | Purpose                                                                                                       |
|----------------|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `kiwi-server`  | Automatic in Kiwi projects, or `/kiwi:kiwi-server`  | Server development guide: architecture rules, idioms, pitfalls, and the full Kiwi-Server documentation        |
| `kiwi-vue`     | Automatic in Kiwi-Vue projects, or `/kiwi:kiwi-vue` | Frontend development guide for Kiwi-Vue 2 (`@werdenktwas/kiwi-vue`), with its documentation                   |
| `kiwi-upgrade` | Manual only: `/kiwi:kiwi-upgrade [detect\|N]`       | Upgrades a Kiwi 1.4 application to 1.5, and its Kiwi-Vue frontends from 1.x to 2.0, with commits and a report |

```
.claude-plugin/marketplace.json        # marketplace "kiwi-framework"
plugins/kiwi/
├── .claude-plugin/plugin.json
├── evals/                             # test cases, see "Testing the skills"
└── skills/
    ├── kiwi-server/
    │   ├── SKILL.md
    │   └── references/                # copied from kiwi-server docs/ by Gradle, don't edit
    ├── kiwi-vue/
    │   ├── SKILL.md
    │   └── references/                # README.md and CHANGELOG.md copied from kiwi-vue by Gradle, don't edit
    └── kiwi-upgrade/
        ├── SKILL.md
        ├── references/
        │   ├── migration-1.5.md       # copied from kiwi-server docs/98-migration-1.5.md by Gradle, don't edit
        │   ├── generic-commands.md
        │   └── kiwi-vue-2.md
        └── scripts/detect-1.4-usages.sh
build.gradle.kts, gradle.properties    # documentation sync, see "Maintenance"
```

## Installation

In Claude Code, add this repository as marketplace and install the plugin:

```
/plugin marketplace add https://github.com/wer-denkt-was/kiwi-skills
/plugin install kiwi@kiwi-framework
```

The same works from a terminal with `claude plugin marketplace add …` and `claude plugin install kiwi@kiwi-framework`,
or from a local checkout with `/plugin marketplace add ./path/to/kiwi-skills`. Updates are installed using
`/plugin marketplace update kiwi-framework`.

To offer the plugin to everyone working on an application project, declare the marketplace in the project's
`.claude/settings.json`; Claude Code then asks team members to install it when they trust the project folder:

```json
{
  "extraKnownMarketplaces": {
    "kiwi-framework": {
      "source": {
        "source": "git",
        "url": "https://github.com/wer-denkt-was/kiwi-skills.git"
      }
    }
  },
  "enabledPlugins": {
    "kiwi@kiwi-framework": true
  }
}
```

Without a marketplace, the skills can also be copied into a project's `.claude/skills/` folder (or `~/.claude/skills/`
for all projects). They are then invoked without the plugin prefix, e.g. `/kiwi-upgrade`. Copy all three, as the
skills refer to each other's references.

## Usage

**Developing with Kiwi**: Nothing to do. In a project using Kiwi-Server or Kiwi-Vue, Claude loads the `kiwi-server` or
`kiwi-vue` skill when a task involves the framework, and reads the documentation it needs.

**Upgrading from Kiwi 1.4 / Kiwi-Vue 1.x**: In the application project, on a clean working tree:

1. `/kiwi:kiwi-upgrade detect`: Claude records the baseline build, creates the branch `upgrade/kiwi-1.5` and commits
   `KIWI-UPGRADE.md` there with all 1.4 usages it found, without changing code. Review the findings, the decisions and
   the expected effort.
2. `/kiwi:kiwi-upgrade`: Claude continues on the branch and migrates the project phase by phase (dependencies, setup,
   Jackson 3, Kiwi API, properties, clients and Kiwi-Vue frontends), committing each phase, then verifies the build
   and tests against the baseline. If interrupted, continue with `/kiwi:kiwi-upgrade <phase number>`.
3. Review the commits, and decide on the items under "Decisions for the user" in `KIWI-UPGRADE.md`, in particular
   the role-prefix change (security relevant) and the client-facing API changes. Delete `KIWI-UPGRADE.md` before
   merging, or keep it as the migration record.

Frontend-only projects using Kiwi-Vue 1.x are upgraded the same way (branch `upgrade/kiwi-vue-2`); the server must be
on Kiwi 1.5, and both are deployed together.

The detection script can also be run without Claude:
`plugins/kiwi/skills/kiwi-upgrade/scripts/detect-1.4-usages.sh <project-dir>`.

## Maintenance

The documentation in the skills is a copy of the documentation of the Kiwi projects, because a plugin is installed
directly from the repository without a build. Gradle checks it out from the source repositories and copies it:

| Task                  | Purpose                                                                                  |
|-----------------------|------------------------------------------------------------------------------------------|
| `./gradlew syncDocs`  | Copies the documentation of the sources into the skills; commit the result               |
| `./gradlew checkDocs` | Fails if the copies differ from the sources (also part of `./gradlew check`/`build`)     |
| `./gradlew fetchDocs` | Only checks out the sources (shallow and sparse) into `build/sources/`, printing commits |

The sources are configured in `gradle.properties`, and can be overridden with `-P` or in `~/.gradle/gradle.properties`:

| Property                | Content                                                                   |
|-------------------------|---------------------------------------------------------------------------|
| `kiwiServer.repository` | Kiwi-Server repository: URL accepted by `git clone`, or a local path      |
| `kiwiServer.ref`        | Branch or tag of the Kiwi-Server documentation (`docs/`)                  |
| `kiwiVue.repository`    | Kiwi-Vue repository: URL or local path                                    |
| `kiwiVue.ref`           | Branch or tag of the Kiwi-Vue documentation (`README.md`, `CHANGELOG.md`) |

Local paths are resolved relative to this directory, e.g. to check unpushed documentation of sibling checkouts:
`./gradlew syncDocs -PkiwiServer.repository=../kiwi-server -PkiwiVue.repository=../kiwi-vue`. Only committed changes
of the given branch are copied. The fetch needs `git` and access to the repositories (SSH keys for SSH URLs).

The files copied per source are defined in `skillDocs()` in `build.gradle.kts`.

- Switch the refs to release tags (or `main`) when a Kiwi release is published, sync, and commit.
- Bump `version` in `plugins/kiwi/.claude-plugin/plugin.json` and in `.claude-plugin/marketplace.json` with each
  release, so installed plugins get updated. `claude plugin validate .` and `claude plugin validate plugins/kiwi`
  check the manifests.
- For a future Kiwi release, add its migration guide to `skillDocs()`, and adapt or add an upgrade skill for it; keep
  `kiwi-upgrade` for applications still on 1.4.

## Testing the skills

The test cases in `plugins/kiwi/evals/` use the format of `claude plugin eval`: each case has a prompt, a description of
the expected outcome and graders. Run them from the plugin directory (these are real, billed model runs):

```
cd plugins/kiwi
claude plugin eval . --allow-tools Write Edit Bash --scaffold --no-publish
```

Each case runs with and without the plugin (3 runs each by default, `--runs 1` for a quick check, `-j 3` to run
cases in parallel). Results are written to `evals/results/` (ignored by git). The runs confine the Bash tool in a
sandbox, which requires `bubblewrap` and `socat` on Linux (and, on Ubuntu 24.04+, unprivileged user namespaces:
`sudo sysctl kernel.apparmor_restrict_unprivileged_userns=0`). `--no-publish` keeps the HTML report local instead of
publishing it to claude.ai, because the test projects are internal code.

| Case                        | Skill          | Input                                                                  |
|-----------------------------|----------------|------------------------------------------------------------------------|
| `detect-reports-findings`   | `kiwi-upgrade` | Kiwi 1.4 shop service with a small web client: `detect` only           |
| `rewrite-generic-commands`  | `kiwi-upgrade` | The same service: phase 5 only (generic commands, handlers, test)      |
| `upgrade-kiwi-vue-frontend` | `kiwi-upgrade` | Kiwi-Vue 1.x todo frontend (frontend-only), with the regenerated model |
| `generic-crud-endpoints`    | `kiwi-server`  | Kiwi 1.5 project service: CRUD endpoints with owner-or-admin access    |

The real test is an actual project: run `/kiwi:kiwi-upgrade detect` there first, and compare the result with a manual
review.
