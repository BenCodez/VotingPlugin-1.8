# Upstream build-only dispositions

Pinned current-main reference: `834bcb84a59b6da40640eac83c20fd97ad1d64c9`.

Each row below was checked against the complete changed-file list and exact patch,
not inferred from its subject. These commits change only the stated build target
or workflow JDK. Their disposition is `OMIT_MODERN_ONLY`; no production fix is
hidden in these rows.

| Upstream commit | Complete semantic change | Java 8 / Bukkit 1.8 decision |
| --- | --- | --- |
| `e35bb0fe744f82e98f203ee6e9756d60c6a9369d` | Existing Maven workflow JDK: 17 → 21 | Omit this modern-main CI JDK change; acceptance uses the actual workspace Java 8 JDK. No workflow is added or claimed to have run. |
| `bbd77d1686318ede0b1a92b0b6c202d5b56bd41b` | Existing Javadoc publishing workflow JDK: 17 → 21 | Omit this modern publishing JDK change. Publishing is outside the authorized scope and was not run. |
| `f47129143e2678f01a0fd8c46e6819092fa971bc` | Provided Spigot API: 26.1.2 → 26.2 | Omit the modern API target update; retain the 1.8 API and Spigot 1.8.8 runtime acceptance target. |

Both fork POMs retain compiler source/target/release 8 and provided
`org.spigotmc:spigot-api:1.8.7-R0.1-SNAPSHOT`; the primary live acceptance target
is Spigot 1.8.8. Existing build, artifact and runtime evidence is recorded in
[java8-sync.md](java8-sync.md). This documentation audit changes no build or
runtime inputs and does not claim a fresh build or a CI/publishing run.

This is a bounded subset of the upstream ledger, not a claim of matching main.
Mixed changes to SimpleAPI, Configurate, annotation processors, dependency
versions and compiler-plugin setup remain subject to their own source and
compatibility audit. A modern release number alone is not evidence that a
library is incompatible with Java 8. Fork release versions remain unchanged.
