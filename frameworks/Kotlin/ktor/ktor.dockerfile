FROM openjdk:11.0.3-jdk-stretch
WORKDIR /app
COPY ktor/gradle gradle
COPY ktor/build.gradle build.gradle
COPY ktor/gradle.properties gradle.properties
COPY ktor/gradlew gradlew
COPY ktor/settings.gradle settings.gradle
COPY ktor/src src
RUN /app/gradlew --no-daemon shadowJar
CMD ["java", "-server", "-XX:+UseParallelGC", "-XX:+UseNUMA", "-Xms2G","-Xmx2G", "-jar", "/app/build/libs/bench.jar", "Netty"]
