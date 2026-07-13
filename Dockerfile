# Stage 1: Build project
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Copy Maven descriptor files and source files
COPY pom.xml .
COPY antispam-api/pom.xml antispam-api/
COPY antispam-infra/pom.xml antispam-infra/
COPY antispam-core/pom.xml antispam-core/
COPY antispam-factor/pom.xml antispam-factor/
COPY antispam-policy/pom.xml antispam-policy/
COPY antispam-punishment/pom.xml antispam-punishment/
COPY antispam-starter/pom.xml antispam-starter/

# Download dependencies offline to cache them
RUN mvn dependency:go-offline -B -pl antispam-starter -am

# Copy source code and build project
COPY . .
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy the built jar to runtime image
COPY --from=builder /build/antispam-starter/target/antispam-starter-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
