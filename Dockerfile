FROM adoptopenjdk/openjdk11:alpine-slim as build
WORKDIR /workspace/app

COPY njams-sdk/pom.xml njams-sdk/pom.xml
COPY njams-sdk/src/ njams-sdk/src/
COPY njams-sdk/target/ njams-sdk/target/

# Run vulnerability scan on build image
FROM build AS vulnscan
COPY --from=aquasec/trivy:latest /usr/local/bin/trivy /usr/local/bin/trivy
RUN trivy rootfs --no-progress --timeout 10m /
