# SDK-381: GitHub Actions Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the Jenkins build with two GitHub Actions workflows — a CI workflow (build, test, coverage, scan, snapshot-deploy to Nexus) and a manual release workflow (publish to GitHub Packages + versioned Javadoc to GitHub Pages).

**Architecture:** A single reactor build at the repo root compiles and tests all three modules in dependency order, so no artifacts are passed between jobs and no Maven coordinates are extracted. CI is two jobs: `build` on `ubuntu-latest` (the gate), and `publish-snapshot` on the self-hosted runner `os0015` (which has Nexus network access and a `settings.xml` carrying the Nexus credentials). The release workflow uses `mvn release:prepare release:perform` with a `github` profile that redirects `distributionManagement` to GitHub Packages, then publishes Javadoc to a per-version subdirectory on the `gh-pages` branch.

**Tech Stack:** GitHub Actions, Maven 3.8+, Java 11 (Temurin), `actions/checkout@v4`, `actions/setup-java@v4`, `actions/upload-artifact@v4`, `aquasecurity/trivy-action@master`, SonarCloud scanner, JaCoCo (`-Psonar`), `gh` CLI (preinstalled on runners).

## Global Constraints

- Java 11, Temurin distribution.
- Only `6.0-dev` triggers GitHub Actions (push + PR). All other branches stay on Jenkins until 6.0 merges.
- Build metadata must be stamped into the filtered resource `njams-sdk/src/main/resources/njams.version`. The build MUST pass `-DrevisionNumberPlugin.revision=<run-number> -DscmBranch=<branch> -DscmCommit=<sha>`. (`current.year` is auto-supplied by `build-helper-maven-plugin`; do not pass it.)
- `gh-pages` is written only by `release.yml`, and only ever adds/replaces a single `/<version>/` subdirectory — it never touches the branch root or other versions. The GitHub Wiki is never touched by any workflow.
- Nexus credentials are NOT in the repo. They live in `~/.m2/settings.xml` on the `os0015` runner, under server IDs matching the `distributionManagement` (`releases`, `snapshots`). `setup-java` on the `publish-snapshot` job must use `overwrite-settings: 'false'` so it does not clobber that file.
- SonarCloud identity (`organization=integration-matters-gmbh`, `projectKey=IntegrationMatters_njams-sdk`, `host=https://sonarcloud.io`) is not secret and is passed as literal `-D` flags in the workflow; only the `SONAR_TOKEN` secret is sensitive.
- Commit message prefix: `SDK-381`.

## Prerequisites (ops / repo configuration — outside this plan)

1. **Self-hosted runner `os0015`** is registered to the repo and online, with a Java-capable environment, network access to Nexus (`http://vslnexus01:8081`), and a `~/.m2/settings.xml` containing Nexus credentials for server IDs `releases` and `snapshots`.
2. **Repository secret** `SONAR_TOKEN` is set.
3. SonarCloud org/key/host are fixed literals in `build.yml` (see Global Constraints).

## Known Blocker (release only — does not affect CI/snapshot)

`njams-messageformat` is pinned to `5.0.1-SNAPSHOT` (`pom.xml` line 48). `mvn release:prepare` refuses to release with SNAPSHOT dependencies. **The release workflow cannot succeed until `njams-messageformat` has a released (non-SNAPSHOT) version and `pom.xml` is updated to consume it.** The snapshot/CI workflow is unaffected. Since snapshot publishing is the current priority, `release.yml` is delivered ready-to-use but will fail at `release:prepare` until this dependency is resolved.

---

### Task 1: Update root `pom.xml` — add `github` profile, remove stale `svn-check` profile

**Files:**
- Modify: `pom.xml` — remove the `svn-check` profile (lines ~403–437), add a `github` profile.

**Interfaces:**
- Produces: Maven profile `github` (activates GitHub Packages `distributionManagement`) — consumed by Task 3 (`release.yml`).

**Rationale for removal:** `svn-check` runs `svn-revision-number-maven-plugin` and auto-activates on Linux. The project moved from SVN to Git years ago — the plugin is dead. (User confirmed removal. The `revisionNumberPlugin.revision` property it used to set is now supplied explicitly via `-D` in the workflows.)

- [ ] **Step 1: Remove the `svn-check` profile**

Delete this entire block from `pom.xml`:

```xml
        <profile>
            <id>svn-check</id>
            <activation>
                <os>
                    <family>unix</family>
                </os>
            </activation>
            <build>
                <plugins>
                    <plugin>
                        <groupId>com.google.code.maven-svn-revision-number-plugin</groupId>
                        <artifactId>svn-revision-number-maven-plugin</artifactId>
                        <executions>
                            <execution>
                                <goals>
                                    <goal>revision</goal>
                                </goals>
                            </execution>
                        </executions>
                        <configuration>
                            <entries>
                                <entry>
                                    <path>${project.basedir}</path>
                                    <prefix>revisionNumberPlugin</prefix>
                                    <depth>infinity</depth>
                                    <reportUnversioned>false</reportUnversioned>
                                    <reportIgnored>false</reportIgnored>
                                    <reportOutOfDate>false</reportOutOfDate>
                                </entry>
                            </entries>
                        </configuration>
                    </plugin>
                </plugins>
            </build>
        </profile>
```

- [ ] **Step 2: Add the `github` profile**

Inside `<profiles>...</profiles>`, after the `sonar` profile, add:

```xml
        <profile>
            <id>github</id>
            <distributionManagement>
                <repository>
                    <id>github</id>
                    <name>GitHub Packages</name>
                    <url>https://maven.pkg.github.com/IntegrationMatters/njams-sdk</url>
                </repository>
            </distributionManagement>
        </profile>
```

- [ ] **Step 3: Verify the pom parses and profiles resolved as expected**

Run: `mvn help:all-profiles -f pom.xml`
Expected: output lists `Profile Id: github` and `Profile Id: sonar`, and does NOT list `Profile Id: svn-check`.

- [ ] **Step 4: Verify a Linux-equivalent build no longer references the svn plugin**

Run: `mvn -B -N validate -f pom.xml`
Expected: `BUILD SUCCESS`, with no `svn-revision-number-maven-plugin` line in the output.

- [ ] **Step 5: Commit**

```bash
git add pom.xml
git commit -m "SDK-381 Add github Maven profile for GitHub Packages; remove dead svn-check profile"
```

---

### Task 2: Create `build.yml` — CI gate + snapshot deploy

**Files:**
- Create: `.github/workflows/build.yml`

**Interfaces:**
- Consumes: repo secret `SONAR_TOKEN`; Nexus credentials from `settings.xml` on the `os0015` runner.
- Produces: CI status check on `6.0-dev`; root-pom + `njams-sdk` snapshots in Nexus (on push only).

**Design:** Two jobs. `build` runs the full reactor (`install` builds all three modules so the samples are compile-verified), with checkstyle and JaCoCo via profiles, then a Sonar analysis scoped to the SDK, a Javadoc-builds-clean check, and a Trivy filesystem scan. `publish-snapshot` rebuilds on `os0015` and deploys only the root pom + SDK to Nexus (`-pl njams-sdk -am`), skipping tests (already run in `build`). No artifact passing — a reactor build is self-contained and the Maven cache keeps re-runs fast.

- [ ] **Step 1: Create `.github/workflows/build.yml`**

```yaml
name: Build nJAMS SDK

on:
  push:
    branches: [6.0-dev]
  pull_request:
    branches: [6.0-dev]

jobs:

  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: maven

      - name: Build and test all modules (with coverage)
        run: >
          mvn -B clean install -Psonar
          -DrevisionNumberPlugin.revision=${{ github.run_number }}
          -DscmBranch=${{ github.ref_name }}
          -DscmCommit=${{ github.sha }}

      # Checkstyle runs after the build (not folded into it): the checkstyle
      # profile binds to the validate phase, and JavadocMethodCheck needs the
      # compiled classes on the classpath to resolve @throws types. Running it
      # here — against the just-built classes, with no intervening clean —
      # keeps those types resolvable.
      - name: Checkstyle (njams-sdk)
        run: mvn -B validate -Pcheckstyle -pl njams-sdk

      - name: SonarCloud analysis (njams-sdk)
        run: >
          mvn -B sonar:sonar -Psonar --file njams-sdk/pom.xml
          -Dsonar.host.url=https://sonarcloud.io
          -Dsonar.organization=integration-matters-gmbh
          -Dsonar.projectKey=IntegrationMatters_njams-sdk
          -Dsonar.token=${{ secrets.SONAR_TOKEN }}

      - name: Verify Javadoc builds without errors
        run: mvn -B javadoc:javadoc --file njams-sdk/pom.xml

      - name: Trivy vulnerability scan
        uses: aquasecurity/trivy-action@master
        with:
          scan-type: fs
          scan-ref: .
          format: table
          exit-code: '1'
          severity: CRITICAL,HIGH

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: njams-sdk/target/surefire-reports/

  publish-snapshot:
    needs: build
    if: github.event_name == 'push'
    runs-on: os0015
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 11 (keep runner's Nexus settings.xml)
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'
          overwrite-settings: 'false'

      - name: Deploy snapshot to Nexus (root pom + njams-sdk)
        run: >
          mvn -B clean deploy -DskipTests -pl njams-sdk -am
          -DrevisionNumberPlugin.revision=${{ github.run_number }}
          -DscmBranch=${{ github.ref_name }}
          -DscmCommit=${{ github.sha }}
```

- [ ] **Step 2: Validate YAML syntax**

Run: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/build.yml')); print('YAML OK')"`
Expected: `YAML OK`

- [ ] **Step 3: Verify the build commands locally (the heart of the `build` job)**

Run, in order (no `clean` before the checkstyle command — checkstyle needs the compiled classes on the classpath to resolve `@throws` types):
```
mvn -B clean install -Psonar -DrevisionNumberPlugin.revision=0 -DscmBranch=local -DscmCommit=localsha
mvn -B validate -Pcheckstyle -pl njams-sdk
```
Expected: both `BUILD SUCCESS`; afterwards `njams-sdk/target/classes/njams.version` contains stamped lines `sdk.version=6.0.0-SNAPSHOT (0)`, `sdk.scmBranch=local`, `sdk.scmCommit=localsha` — confirming the `-D` properties feed the filtered resource. (Folding `-Pcheckstyle` into `clean install` fails: the checkstyle profile binds to the `validate` phase, which runs before `compile`, so `JavadocMethodCheck` cannot resolve the `@throws` exception types.)

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/build.yml
git commit -m "SDK-381 Add GitHub Actions CI workflow with Nexus snapshot deploy"
```

---

### Task 3: Create `release.yml` — manual release to GitHub Packages + versioned Javadoc

**Files:**
- Create: `.github/workflows/release.yml`

**Interfaces:**
- Consumes: `github` Maven profile (Task 1); built-in `GITHUB_TOKEN`.
- Produces: Git tag `<releaseVersion>`; artifacts in GitHub Packages; a GitHub Release with JARs; Javadoc at `gh-pages/<releaseVersion>/`.

**Design notes:**
- `release:prepare` strips `-SNAPSHOT`, commits the release POMs, tags, then bumps to `developmentVersion`. `-DscmCommentPrefix="[skip ci] "` keeps those commits from triggering `build.yml`.
- `release:perform` checks out the tag into `target/checkout/` and runs `mvn deploy` there. `-Darguments="-Pgithub -DskipTests"` makes that inner build use the GitHub Packages `distributionManagement` and skip re-testing.
- `setup-java` writes a `settings.xml` server entry `github` authenticated with the token, so `deploy` to `maven.pkg.github.com` works.
- The GitHub Release is created with the built-in `gh` CLI (no third-party action).
- Javadoc publishing is gated behind the `publishJavadoc` boolean input (default off) so rc/beta/milestone releases do not publish docs. The `publish-javadoc` job runs only when the box is ticked.
- When it does run, `peaceiris/actions-gh-pages@v4` publishes into the `<version>` subdirectory with `keep_files: true`, which preserves every other file already on `gh-pages` (other versions stay intact) and creates the branch on first run. This is the no-clobber guarantee, handled by the action rather than hand-rolled git.

- [ ] **Step 1: Create `.github/workflows/release.yml`**

```yaml
name: Release nJAMS SDK

on:
  workflow_dispatch:
    inputs:
      releaseVersion:
        description: 'Release version (e.g. 6.0.0)'
        required: true
      developmentVersion:
        description: 'Next development version (e.g. 6.0.1-SNAPSHOT)'
        required: true
      publishJavadoc:
        description: 'Publish Javadoc to GitHub Pages (leave unchecked for rc/beta/milestone releases)'
        type: boolean
        required: false
        default: false

permissions:
  contents: write
  packages: write

jobs:

  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          ref: 6.0-dev
          fetch-depth: 0

      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: maven
          server-id: github
          server-username: GITHUB_ACTOR
          server-password: GITHUB_TOKEN

      - name: Configure Git identity and token credentials
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git config --global url."https://x-access-token:${GITHUB_TOKEN}@github.com/".insteadOf "https://github.com/"
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Maven release (prepare + perform → GitHub Packages)
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GITHUB_ACTOR: ${{ github.actor }}
        run: >
          mvn -B release:prepare release:perform
          -DreleaseVersion=${{ github.event.inputs.releaseVersion }}
          -DdevelopmentVersion=${{ github.event.inputs.developmentVersion }}
          -DscmCommentPrefix="[skip ci] "
          -Dgoals=deploy
          -Darguments="-Pgithub -DskipTests"

      - name: Create GitHub Release with JARs
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: >
          gh release create ${{ github.event.inputs.releaseVersion }}
          --title "Release ${{ github.event.inputs.releaseVersion }}"
          --generate-notes
          target/checkout/njams-sdk/target/njams-sdk-${{ github.event.inputs.releaseVersion }}.jar
          target/checkout/njams-sdk/target/njams-sdk-${{ github.event.inputs.releaseVersion }}-sources.jar

  publish-javadoc:
    needs: release
    if: ${{ inputs.publishJavadoc }}
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.event.inputs.releaseVersion }}

      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          java-version: '11'
          distribution: 'temurin'
          cache: maven

      - name: Build Javadoc
        run: |
          mvn -B -N install --file pom.xml
          mvn -B javadoc:javadoc --file njams-sdk/pom.xml

      - name: Publish Javadoc to gh-pages/<version>
        uses: peaceiris/actions-gh-pages@v4
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          publish_dir: njams-sdk/target/site/apidocs
          destination_dir: ${{ github.event.inputs.releaseVersion }}
          keep_files: true
          user_name: github-actions[bot]
          user_email: github-actions[bot]@users.noreply.github.com
          commit_message: "SDK-381 Publish Javadoc for ${{ github.event.inputs.releaseVersion }}"
```

- [ ] **Step 2: Validate YAML syntax**

Run: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/release.yml')); print('YAML OK')"`
Expected: `YAML OK`

- [ ] **Step 3: Sanity-check the release prerequisites (informational)**

Run: `grep -n "njams-messageformat.version" pom.xml`
Expected: shows `5.0.1-SNAPSHOT`. This documents the known blocker: `release:prepare` will fail until this is a released version. Do NOT attempt to work around it here — surface it to the user.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "SDK-381 Add GitHub Actions manual release workflow"
```

---

### Task 4: Remove the Jenkins build files

**Files:**
- Delete: `Jenkinsfile`
- Delete: `Dockerfile` (only used by the Jenkins Trivy stage; Trivy now runs as a GitHub Action)

**Interfaces:**
- Produces: nothing.

**Note:** Done last so the old pipeline definition remains available as reference until the new workflows are committed.

- [ ] **Step 1: Delete the files**

```bash
git rm Jenkinsfile Dockerfile
```

- [ ] **Step 2: Verify**

Run: `git status`
Expected: shows `deleted: Jenkinsfile` and `deleted: Dockerfile`.

- [ ] **Step 3: Commit**

```bash
git commit -m "SDK-381 Remove Jenkinsfile and Dockerfile — superseded by GitHub Actions"
```

---

## Self-Review

**Spec coverage:**

| Requirement | Task |
|---|---|
| CI on push + PR to `6.0-dev` only | Task 2 (`on:` filter) |
| Build all modules / verify samples compile | Task 2 (reactor `install`) |
| Checkstyle gate | Task 2 (`-Pcheckstyle`, validate phase) |
| Tests + JaCoCo coverage | Task 2 (`-Psonar`) |
| SonarCloud analysis | Task 2 (`sonar:sonar` scoped to SDK) |
| Javadoc verified, not published, in CI | Task 2 (`javadoc:javadoc`, no Pages push) |
| Trivy scan | Task 2 (`trivy-action`, fs scan) |
| Snapshot → Nexus, push only, internal runner | Task 2 (`publish-snapshot`, `os0015`, `if: push`) |
| Build metadata stamped into `njams.version` | Global constraint + Task 2 `-D` flags (verified Step 3) |
| `github` profile → GitHub Packages | Task 1 |
| `svn-check` removed (confirmed) | Task 1 |
| Manual release, explicit versions | Task 3 (`workflow_dispatch` inputs) |
| Release → GitHub Packages | Task 3 (`-Darguments=-Pgithub`, `deploy`) |
| GitHub Release with JARs | Task 3 (`gh release create`) |
| Versioned Javadoc, never clobbers other versions/wiki | Task 3 (`gh-pages/<version>` script) |
| Javadoc publishing optional (off for rc/beta) | Task 3 (`publishJavadoc` boolean input + job `if:`) |
| `[skip ci]` on release commits | Task 3 (`-DscmCommentPrefix`) |
| Jenkinsfile + Dockerfile removed | Task 4 |
| Nexus creds not in repo | Global constraint + Task 2 (`settings.xml` on `os0015`, `overwrite-settings: false`) |

**Placeholder scan:** none.

**Type consistency:** no cross-task code types; interfaces are the `github` Maven profile (Task 1 → Task 3) and external config (secrets/vars/runner label), all referenced consistently.
