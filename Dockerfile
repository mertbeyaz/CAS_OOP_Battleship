FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Jar ins Image kopieren
COPY target/battleshipbackend-0.0.1-SNAPSHOT.jar app.jar

# Expose Port (info-only, für Doku)
EXPOSE 8080

# Java Prozess starten
ENTRYPOINT ["java", "-jar", "app.jar"]