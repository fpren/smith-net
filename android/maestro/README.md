# SmithNet — Maestro E2E (Solo Mode)

`smithnet_solo_e2e.yaml` drives the real Android UI through the Solo flow:
Plan -> Job -> Tasks -> Invoice, asserting the invoice total **$1,053.95**
(materials $293.95 + labor $760.00).

## Prerequisites
1. Install Maestro: `curl -Ls https://get.maestro.mobile.dev | bash`
2. Build + install the debug APK: `cd android && ./gradlew installDebug`
3. Start the backend and bridge it to the device:
   ```
   cd backend && npm run dev          # backend on :3030
   adb reverse tcp:3030 tcp:3030      # device 127.0.0.1:3030 -> host
   ```
4. Recommended: start logged-in + onboarded. The login/onboarding blocks are
   guarded (run only if those screens appear), but the onboarding trade-search
   dropdown is awkward to automate. Seed the Solo account the instrumented test
   uses (`solo-e2e@smithnet.test`) by running `SoloPipelineE2ETest` once, or log
   in manually before running Maestro.

## Run
```
maestro test android/maestro/smithnet_solo_e2e.yaml
```

## How elements are matched
- The app root enables `testTagsAsResourceId = true` (MainActivity), so Maestro
  matches the typed input fields by stable `solo_e2e_*` ids.
- Buttons/labels are matched by visible monospace text. Maestro treats `text:`
  as a **regex**, so bracketed labels are escaped (e.g. `\[Clients\]`).

## Verified vs. not
- VERIFIED here: the app compiles with the added `testTag`s; the YAML is
  syntactically valid.
- NOT executed here: Maestro itself was not run in this environment. On first
  run, confirm the two `CONFIRM-NAV` points in the YAML (the Clients and Plan
  entry controls) against your build, and note that "CREATE JOB" from a proposal
  may require the proposal to be PROPOSED/CONFIRMED first depending on build.

## Relationship to the instrumented test
The deterministic data pipeline (auth -> client -> plan -> job -> tasks ->
invoice = $1,053.95, plus Scheduled status, calendar entry, client note, invoice
address, PDF export) is verified headlessly and runs green via
`SoloPipelineE2ETest` (androidTest). This Maestro flow is the UI-driven
counterpart that matches the spec's named framework/run command.
```
JAVA_HOME=<jdk17> ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.guildofsmiths.trademesh.e2e.SoloPipelineE2ETest
```
