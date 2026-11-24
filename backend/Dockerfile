# --- Étape 1 : compilation ---------------------------------------------
FROM eclipse-temurin:21-jdk AS compilation
WORKDIR /source

# Dépendances d'abord, pour profiter du cache Docker.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/backend-*.jar extract --layers --launcher --destination extrait

# --- Étape 2 : exécution ------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /application

RUN addgroup -S boutique && adduser -S boutique -G boutique
USER boutique

# Couches du jar Spring Boot, de la moins à la plus souvent modifiée.
COPY --from=compilation /source/extrait/dependencies/ ./
COPY --from=compilation /source/extrait/spring-boot-loader/ ./
COPY --from=compilation /source/extrait/snapshot-dependencies/ ./
COPY --from=compilation /source/extrait/application/ ./

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Duser.timezone=Africa/Dakar"
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
