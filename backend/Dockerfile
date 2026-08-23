FROM m.daocloud.io/docker.io/library/maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY docker/maven-settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -q -s /root/.m2/settings.xml -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -s /root/.m2/settings.xml -DskipTests package
FROM m.daocloud.io/docker.io/library/eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /build/target/aiquestion-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
