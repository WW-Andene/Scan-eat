FROM gradle:8.14.4-jdk21 AS build
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY src ./src
COPY public ./public
RUN gradle installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/install/scan-eat-kotlin ./
COPY public ./public
ENV PORT=5173
EXPOSE 5173
CMD ["./bin/scan-eat-kotlin"]
