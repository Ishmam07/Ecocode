# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests


# Runtime stage
FROM eclipse-temurin:21-jre-jammy

# Install Python
RUN apt-get update && \
    apt-get install -y python3 python3-pip && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Spring Boot JAR
COPY --from=build /app/target/scheduler-0.0.1-SNAPSHOT.jar app.jar

# Copy Python dependencies
COPY src/main/java/com/ecocode/scheduler/requirements.txt .

# Install Python dependencies
RUN pip3 install --no-cache-dir -r requirements.txt

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
