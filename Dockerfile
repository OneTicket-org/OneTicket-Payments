# Build the Spring Boot executable with the project's Gradle Wrapper.
FROM eclipse-temurin:25.0.5_8-jdk-noble AS build

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ ./gradle/
RUN chmod +x ./gradlew

COPY src/ ./src/
# The cache mount keeps ~/.gradle (Gradle distribution + dependencies) between builds,
# so a source change no longer re-downloads everything.
# with id to avoid cache collision with other builds, and target to specify the path to cache.
RUN --mount=type=cache,id=gradle-cache-payments,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

# Run the packaged application on a JRE-only image.
FROM eclipse-temurin:25.0.5_8-jre-noble AS runtime

WORKDIR /app

RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app

COPY --from=build --chown=app:app /workspace/build/libs/*.jar /app/app.jar

USER app
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
