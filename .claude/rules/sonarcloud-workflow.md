---
globs: "njams-sdk/**"
---

# SonarCloud Issues

When working on SonarCloud findings, **evaluate every issue individually before fixing it.** Not every reported issue
is a genuine defect — some are false positives given this project's threading model, lifecycle guarantees, public-API
constraints, or intentional design decisions. The `njams-sonarcloud-triage` skill carries the step-by-step procedure.

**Before fixing any issue:**
1. Read the flagged code in context.
2. Determine whether the rule violation is a real risk in practice (given how the code is actually used) or whether the
   project's constraints make the warning inapplicable.
3. If the issue is a real bug, fix it following the normal bug-fix workflow (`njams-bug-fix`). A fix that changes
   existing public/protected API is still bound by `public-api-design.md` — raise it instead of changing the API.
4. If you consider the issue a false positive, **present your reasoning to the user and wait for explicit confirmation
   before dismissing or suppressing it.** Include: what the rule flags, why it does not apply here, and what risk (if
   any) exists. Do not suppress a Sonar issue unilaterally.

**When starting work on SonarCloud findings, query SonarCloud first** to get the current list of open issues — do not
rely on a previously fetched snapshot.

**Before triaging, consult `docs/sonarcloud-decisions.md`** — the decision register of previously handled findings.
Findings rejected as false positives recur every time Sonar re-analyses; if a rule there is flagged again on the same
construct and the rationale still holds, re-apply the recorded decision (re-mark it *False Positive* / *Won't Fix*)
rather than changing correct code. Record new triage decisions there.

**Findings in the deprecated Kafka transport and Argos metrics** (`communication/kafka/**`, `argos/**`) fall under
`kafka-argos-deprecated.md`: they are not worked on proactively, only when the user asks.

**SonarCloud project coordinates** (needed to query the API):

| Field        | Value                         |
|--------------|-------------------------------|
| Host         | `https://sonarcloud.io`       |
| Organization | `integration-matters-gmbh`    |
| Project key  | `IntegrationMatters_njams-sdk` |

The project is private, so API calls require a token. The GitHub build workflow gets it from the `SONAR_TOKEN`
repository secret; the same value is available locally as `SONAR_TOKEN`. Authenticate with HTTP basic auth using the token as the username and
an empty password, e.g.
`curl -u "$SONAR_TOKEN:" "https://sonarcloud.io/api/issues/search?componentKeys=IntegrationMatters_njams-sdk&organization=integration-matters-gmbh&resolved=false&branch=<branch>"`.

Analysis covers only the `njams-sdk` module (the `Sonar analysis` step of `.github/workflows/build.yml`). The sample
modules (`njams-sdk-sample-client`, `njams-sdk-sample-app`) are deliberately excluded — they set `sonar.skip` — and
their findings are never triaged; issues still listed for them on an older analysis are stale. **Every pushed branch
the build workflow runs on (`master`, `N.N.X`, `N.N-dev`, and any branch it is started on manually) is analysed under
its own name**; pull requests are not analysed. Always pass `&branch=<current git branch>`; without it the query returns SonarCloud's main
branch (`master`), which is not the branch being worked on. The Sonar step does not wait for the quality gate, so a
red gate does **not** fail the build — check the gate status in SonarCloud
(`api/qualitygates/project_status?projectKey=IntegrationMatters_njams-sdk&branch=<branch>`).
