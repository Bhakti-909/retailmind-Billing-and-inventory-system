FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/supplier-service-*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
