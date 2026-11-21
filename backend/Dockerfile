# The Spring Boot API. Built in one stage, run from a small JRE image in another.
FROM eclipse-temurin:21.0.13_9-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY src src
# The cache mount keeps Maven and every dependency between builds on the same machine, so a code change
# downloads nothing. A download can still fail for a moment (Maven Central has answered 404, DNS "try again"),
# and the wrapper fetches Maven itself with no retry: the build tries three times before it gives up.
RUN --mount=type=cache,target=/root/.m2 \
    for attempt in 1 2 3; do \
        sh mvnw -B -q -DskipTests package && break; \
        [ "$attempt" = 3 ] && exit 1; \
        echo "Build failed on attempt $attempt, probably a download; trying again"; sleep 15; \
    done \
    # Split the jar into layers: dependencies change rarely, so their layer stays cached between releases
    && java -Djarmode=tools -jar target/tameio-core-*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21.0.13_9-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=10 \
    CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:8080/actuator/health/readiness"]
# Size the heap from the container's memory limit, not the host's
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
