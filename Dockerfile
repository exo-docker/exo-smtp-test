# syntax=docker/dockerfile:1
#
# SMTP configuration tester for eXo Platform: one jar, configured with the same
# EXO_MAIL_* variables as exoplatform/exo-enterprise, sending through the eXo
# kernel MailService. Independent from the eXo image.
#
# Build: docker build -t exoplatform/exo-smtp-test .
# Run:   docker run --rm -e EXO_MAIL_SMTP_HOST=smtp.example.org exoplatform/exo-smtp-test --to me@example.org

ARG BASE_IMAGE=exoplatform/jdk:openjdk-21-ubuntu-2604

FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B package

FROM ${BASE_IMAGE}
ARG VERSION=dev
ARG REVISION=unknown
LABEL org.opencontainers.image.title="exo-smtp-test" \
      org.opencontainers.image.description="Single-jar SMTP tester configured with the EXO_MAIL_* variables of the eXo Platform image" \
      org.opencontainers.image.source="https://github.com/exo-docker/exo-smtp-test" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${REVISION}"
COPY --from=build /src/target/exo-smtp-test.jar /opt/exo-smtp-test/exo-smtp-test.jar
# same uid as the eXo image
RUN useradd --no-create-home -u 999 --user-group --shell /usr/sbin/nologin --no-log-init exo
USER exo
ENTRYPOINT ["java", "-jar", "/opt/exo-smtp-test/exo-smtp-test.jar"]
CMD ["--help"]
