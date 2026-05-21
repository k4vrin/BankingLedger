FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

ARG APP_VERSION=0.0.1-SNAPSHOT
ARG VCS_REF=local
ARG BUILD_DATE=unknown
ARG SOURCE_REPOSITORY=unknown

LABEL org.opencontainers.image.title="Mini Core Banking Ledger API" \
      org.opencontainers.image.description="Spring Boot core banking ledger portfolio API" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.source="${SOURCE_REPOSITORY}"

RUN addgroup --system banking-ledger && adduser --system --ingroup banking-ledger banking-ledger
USER banking-ledger

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
