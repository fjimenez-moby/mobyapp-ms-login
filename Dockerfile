# Multi-stage build para Login Service con GitHub Packages

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Recibir credenciales de GitHub como build arguments
ARG GITHUB_TOKEN
ARG GITHUB_USERNAME

# Debug: Verificar que las variables existen (sin mostrar el token completo)
RUN echo "Checking build args..." && \
    if [ -z "$GITHUB_TOKEN" ]; then \
        echo "ERROR: GITHUB_TOKEN is not set"; \
    else \
        echo "GITHUB_TOKEN is set (first 10 chars: ${GITHUB_TOKEN:0:10}...)"; \
    fi && \
    if [ -z "$GITHUB_USERNAME" ]; then \
        echo "ERROR: GITHUB_USERNAME is not set"; \
    else \
        echo "GITHUB_USERNAME is set: $GITHUB_USERNAME"; \
    fi

# Copiar archivos de configuración de Maven
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Crear settings.xml con credenciales de GitHub para acceder a packages privados
RUN mkdir -p /root/.m2 && \
    echo '<?xml version="1.0" encoding="UTF-8"?>' > /root/.m2/settings.xml && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"' >> /root/.m2/settings.xml && \
    echo '          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"' >> /root/.m2/settings.xml && \
    echo '          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">' >> /root/.m2/settings.xml && \
    echo '  <servers>' >> /root/.m2/settings.xml && \
    echo '    <server>' >> /root/.m2/settings.xml && \
    echo '      <id>github</id>' >> /root/.m2/settings.xml && \
    echo "      <username>${GITHUB_USERNAME}</username>" >> /root/.m2/settings.xml && \
    echo "      <password>${GITHUB_TOKEN}</password>" >> /root/.m2/settings.xml && \
    echo '    </server>' >> /root/.m2/settings.xml && \
    echo '  </servers>' >> /root/.m2/settings.xml && \
    echo '</settings>' >> /root/.m2/settings.xml

# Verificar que settings.xml se creó correctamente
RUN echo "Settings.xml created:" && cat /root/.m2/settings.xml | grep -v password

# Intentar descargar dependencias (permitir que falle)
RUN mvn dependency:resolve -B || echo "Some dependencies may not be resolved yet"

# Copiar código fuente
COPY src ./src

# Compilar la aplicación (esto descargará la librería si settings.xml está bien)
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Crear usuario no-root
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copiar el JAR compilado
COPY --from=build /app/target/*.jar app.jar

# Cambiar permisos
RUN chown -R appuser:appgroup /app

USER appuser

# Exponer puerto
EXPOSE ${LOGIN_PORT:-8085}

# Variables de entorno
ENV JAVA_OPTS="-Xmx128m -Xms64m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -Xss512k"

# Healthcheck
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:${LOGIN_PORT:-8085}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

