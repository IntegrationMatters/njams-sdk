FROM adoptopenjdk/openjdk11:alpine-slim as build
WORKDIR /workspace/app

COPY njams-sdk/pom.xml njams-sdk/pom.xml
COPY njams-sdk/src/ njams-sdk/src/
COPY njams-sdk/target/ njams-sdk/target/

COPY njams-sdk-communication-cloud/pom.xml njams-sdk-communication-cloud/pom.xml
COPY njams-sdk-communication-cloud/src/ njams-sdk-communication-cloud/src/
COPY njams-sdk-communication-cloud/target/ njams-sdk-communication-cloud/target/

# Run vulnerability scan on build image
FROM build AS vulnscan
COPY --from=aquasec/trivy:latest /usr/local/bin/trivy /usr/local/bin/trivy
RUN trivy rootfs --no-progress /