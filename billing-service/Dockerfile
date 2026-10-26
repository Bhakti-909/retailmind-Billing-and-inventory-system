FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/billing-service-*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
