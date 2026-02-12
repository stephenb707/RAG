# runner/Dockerfile

# --- build stage ---
    FROM maven:3.9-eclipse-temurin-21 AS build
    WORKDIR /app
    
    COPY pom.xml .
    COPY src ./src
    RUN mvn -B -ntp -DskipTests package
    
    # --- runtime stage (JDK so javac exists) ---
    FROM eclipse-temurin:21-jdk
    WORKDIR /app
    
    COPY --from=build /app/target/*.jar /app/app.jar
    
    EXPOSE 8090
    ENTRYPOINT ["java","-jar","/app/app.jar"]
    