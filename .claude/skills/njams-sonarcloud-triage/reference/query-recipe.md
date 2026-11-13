# SonarCloud Query Recipe

**Coordinates:**

| Field        | Value                          |
|--------------|--------------------------------|
| Host         | `https://sonarcloud.io`        |
| Organization | `integration-matters-gmbh`     |
| Project key  | `IntegrationMatters_njams-sdk` |

**Auth:** HTTP basic auth, token as username, empty password. Token is `$SONAR_TOKEN` (GitHub repository
secret `SONAR_TOKEN`; also available in the local environment).

**Branch:** always the branch being worked on — `BRANCH=$(git branch --show-current)`. The GitHub build
workflow analyses every branch it runs on (`master`, `N.N.X`, `N.N-dev`, and any branch it is started on
manually) under its own name; without `&branch=` the
API answers for `master`.

**Analysed branches and their gate status:**

```bash
curl -s -u "$SONAR_TOKEN:" \
  "https://sonarcloud.io/api/project_branches/list?project=IntegrationMatters_njams-sdk"
```

**Open issues** (paged, `ps` ≤ 500; add `&rules=java:S3776` or `&types=BUG` to narrow):

```bash
curl -s -u "$SONAR_TOKEN:" \
  "https://sonarcloud.io/api/issues/search?componentKeys=IntegrationMatters_njams-sdk&organization=integration-matters-gmbh&resolved=false&branch=$BRANCH&ps=500&p=1"
```

**Quality gate status** (the build workflow does not wait for it):

```bash
curl -s -u "$SONAR_TOKEN:" \
  "https://sonarcloud.io/api/qualitygates/project_status?projectKey=IntegrationMatters_njams-sdk&branch=$BRANCH"
```
