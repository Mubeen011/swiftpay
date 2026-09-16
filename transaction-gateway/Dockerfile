FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/transaction-gateway-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "app.jar"]