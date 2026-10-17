# ============================================================
# Dockerfile multi-etapa para RentaTools (Java 21 + Spring Boot 3.5.x)
# ============================================================

# Etapa 1: Compilación y empaquetado
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copiar archivos de Maven Wrapper y dependencias del proyecto
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copiar código fuente y compilar artefacto
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Etapa 2: Runtime ligero en contenedor final
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="RentaTools Engineering Team"

RUN addgroup -S rentatools && adduser -S rentatools -G rentatools
USER rentatools:rentatools

WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=UTC

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
