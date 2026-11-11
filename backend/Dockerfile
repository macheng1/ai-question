FROM m.daocloud.io/docker.io/library/maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build

# The build context is the backend directory. Keep dependency resolution in a
# separate layer so source-only changes can reuse the Maven dependency cache.
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
COPY lib/eds/repository ./lib/eds/repository
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -q -s /root/.m2/settings.xml -DskipTests dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -q -s /root/.m2/settings.xml -DskipTests package && \
    cp target/aiquestion-api-*.jar target/app.jar

FROM m.daocloud.io/docker.io/library/eclipse-temurin:17-jre-jammy
WORKDIR /app

# Runtime default; override with -e STORAGE_PROVIDER=aliyun when needed.
ENV STORAGE_PROVIDER=eds

# Do not run the application as root inside the container.
RUN groupadd --system --gid 10001 app && \
    useradd --system --uid 10001 --gid app --home-dir /app --no-create-home app
COPY --from=builder --chown=app:app /build/target/app.jar ./app.jar

USER 10001:10001
EXPOSE 8080

# Respect the container memory limit and fail fast on an OOM instead of
# leaving a partially healthy Java process running.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
