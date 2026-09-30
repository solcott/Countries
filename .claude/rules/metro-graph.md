---
paths:
  - "shared/**"
  - "shared-compose/**"
---

# The Metro graphs — `ComposeGraph` and `CoreGraph`

Two rules that produce confusing errors rather than obvious ones:

1. **Contributions are resolved on the compile classpath of the module that declares
   `@DependencyGraph`.** Metro locates `metro.hints` during graph supertype generation, so a module
   added downstream (in an app module) is too late and its providers simply do not appear. That is
   why `ComposeGraph` lives in `shared-compose`.
   *Symptom:* `[Metro/MissingBinding] No binding found for …`.
2. **Contributing modules must be `api`, not `implementation`, on the graph module.** Contributed
   interfaces become supertypes of the generated graph.
   *Symptom:* `Cannot access '…NetworkProviders' which is a supertype of 'ComposeGraph'`.

- `ComposeGraph` exposes `Circuit` and is shared by every Compose app; none declares its own graph.
  `CoreGraph` exposes repositories and no Compose types — it is what `:apple` uses.
- `graph.circuitSaver` exists for the two call sites that hoist a backstack (`:web`, `:desktop`);
  everything else gets it from `LocalCircuitSaver`.
- The root `Logger` is provided here, by `LoggingProviders`, and injected everywhere else.

## Tests here

- `ComposeGraphSaverTest` (commonTest) must list every `Screen`. `ComposeGraphSaverRoundTripTest`
  sits in `jvmTest` because `testAndroidHostTest`'s stub `Bundle` saves nothing.
- **The graph test binary needs `linkerOpts("-lsqlite3")`.** A klib records no linker options, so it
  otherwise fails at link with undefined `_sqlite3_*` symbols.
- **`karma.config.d/` raises `browserNoActivityTimeout`.** The ~15 MB bundle otherwise disconnects on
  CI and reports *"did not discover any tests"*.

## Verify

`./gradlew assembleDebug :shared-compose:allTests :desktop:packageUberJarForCurrentOS`. Graph errors
surface at the module declaring `@DependencyGraph`, so build a consumer.
