# Guía de Compilación y Construcción — RentaTools

> Plataforma de Renta de Herramientas y Maquinaria  
> Monolito Modular con Arquitectura Hexagonal y DDD  
> Java 21 · Spring Boot 3.5.x · Maven Wrapper · ArchUnit

---

## 1. Requisitos Previos

Antes de compilar y ejecutar el proyecto, asegúrese de contar con el siguiente entorno configurado:

| Requisito | Versión Mínima | Comprobación |
|---|---|---|
| **Java Development Kit (JDK)** | **Java 21 LTS** (Eclipse Temurin, OpenJDK u Oracle) | `java -version` |
| **Git** | 2.x | `git --version` |
| **Maven Wrapper** | Incluido en el repositorio (`mvnw` y `mvnw.cmd`) | `.\mvnw.cmd -v` |
| **Docker Desktop / Engine** | Docker 24+ con Docker Compose v2 (necesario para Testcontainers y BD) | `docker --version` |

> [!IMPORTANT]
> El proyecto utiliza **Java 21** de forma estricta. Verifique que su variable de entorno `JAVA_HOME` apunte a la instalación de JDK 21.

---

## 2. Estructura de Perfiles del Proyecto

El sistema cuenta con perfiles de configuración preparados para diferentes ciclos de vida:

| Perfil | Archivo de Configuración | Propósito |
|---|---|---|
| `default` | `application.yml` | Configuración base del sistema, variables por defecto y puerto 8080. |
| `dev` | `application-dev.yml` | Entorno de desarrollo local con reloj simulado ajustable (`/api/v1/dev/clock`) y seeder de usuarios (`DevUsersSeeder`). |
| `prod` | `application-prod.yml` | Entorno de producción optimizado, compresión HTTP activada, logging controlado y variables obligatorias vía entorno. |
| `test` | `application-test.yml` | Entorno de pruebas automatizadas con Testcontainers (MySQL 8.4) y claves JWT de test. |

---

## 3. Comandos de Compilación Paso a Paso

### 3.1 Limpieza y Verificación de Sintaxis

Para limpiar artefactos anteriores y compilar los archivos de código fuente:

```powershell
# En Windows (PowerShell / CMD)
.\mvnw.cmd clean compile

# En Linux / macOS
./mvnw clean compile
```

### 3.2 Compilación del Código de Pruebas

Para validar que las pruebas unitarias y de integración compilan sin errores:

```powershell
.\mvnw.cmd test-compile
```

### 3.3 Empaquetado del Artefacto (JAR ejecutable)

Para generar el archivo ejecutable (`JAR`) omitiendo la ejecución de pruebas (ideal para despliegue rápido o CI):

```powershell
.\mvnw.cmd clean package -DskipTests
```

El artefacto generado quedará ubicado en:
```
target/rentatools-0.0.1-SNAPSHOT.jar
```

---

## 4. Ejecución del Conjunto de Pruebas

El proyecto implementa una rigurosa suite de pruebas unitarias, de integración con contenedores reales y reglas de pureza arquitectónica:

### 4.1 Ejecución Total de Pruebas

```powershell
.\mvnw.cmd test
```

Este comando ejecuta:
1. **Reglas de Arquitectura Hexagonal y Modularidad (ArchUnit)**:
   - Pureza de capas (`HexagonalLayersTest`): Prohíbe dependencias de frameworks (`org.springframework`, `jakarta`, `lombok`) en las capas de `domain` y `application`.
   - Fronteras entre módulos (`ModuleBoundariesTest`): Garantiza que los bounded contexts no tengan ciclos y que la comunicación sea únicamente a través de interfaces `.api` o eventos de integración.
2. **Pruebas de Dominio Unitarias (JUnit 5 + AssertJ)**:
   - Invariantes de `User`, `Money`, `DateRange`, `Invoice`, `SecurityDeposit`, políticas de liquidación (`SettlementCalculator`) y mora (`LateFeePolicy`).
3. **Pruebas de Casos de Uso y Aplicación**:
   - Pruebas con repositorios y emisores de eventos en memoria sin levantar el contenedor Spring.
4. **Pruebas Web (@WebMvcTest)**:
   - Controladores REST de autenticación, usuarios, facturación y reportes, validando autorizaciones JWT (200, 400, 401, 403) y RFC 9457 `ProblemDetail`.
5. **Pruebas de Integración y Persistencia (Testcontainers)**:
   - `IamPersistenceIT`: Mapeo JPA contra MySQL 8.4 real y rotación de tokens.
   - `BillingPersistenceIT`: Numeración concurrente de facturas, transacciones y cascadas.
   - `BillingFlowIT`: Ciclo de eventos síncronos entre reservas y facturación.
   - `FullRentalFlowIT`: Ciclo E2E completo desde login real hasta reporte de ingresos.

### 4.2 Ejecución de una Prueba Específica

Para correr únicamente una clase de prueba:

```powershell
# Pruebas de arquitectura
.\mvnw.cmd test -Dtest=HexagonalLayersTest

# Prueba E2E
.\mvnw.cmd test -Dtest=FullRentalFlowIT
```

---

## 5. Ejecución en Modo Desarrollo

Para arrancar el servidor embebido localmente con el perfil de desarrollo:

```powershell
# 1. Iniciar la base de datos MySQL 8.4 con Docker Compose
docker compose up -d mysql

# 2. Arrancar la aplicación con el perfil 'dev'
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

Una vez iniciada la aplicación:
- **API REST**: `http://localhost:8080/api/v1`
- **Documentación Swagger UI / OpenAPI**: `http://localhost:8080/swagger-ui.html`
- **Especificación OpenAPI (JSON)**: `http://localhost:8080/v3/api-docs`
