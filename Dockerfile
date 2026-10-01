# Build stage: compile and package the Spring Boot jar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Runtime stage: JRE plus LibreOffice, which renders Office documents with their original layout
FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        libreoffice-writer-nogui libreoffice-calc-nogui libreoffice-impress-nogui \
        # Metric-compatible stand-ins for Arial/Times/Courier (Liberation) and Calibri/Cambria
        # (Carlito/Caladea) keep line and page breaks where the original has them
        fonts-liberation fonts-crosextra-carlito fonts-crosextra-caladea \
        fonts-dejavu-core fonts-noto-core \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
RUN useradd --system --uid 10001 --create-home app
COPY --from=build /app/target/itext-pdf-generator-*.jar app.jar
USER app

# Cloud Run sets PORT (default 8080); application.properties reads it
ENV PORT=8080
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
