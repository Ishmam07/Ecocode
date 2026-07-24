FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update && \
    apt-get install -y python3 python3-pip maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pom.xml .
COPY src ./src
COPY requirements.txt .

RUN pip3 install --no-cache-dir -r requirements.txt

RUN mvn clean package -DskipTests

EXPOSE 8080

CMD ["java", "-jar", "target/scheduler-0.0.1-SNAPSHOT.jar"]
