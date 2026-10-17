# RentaTools — Plataforma de Renta de Herramientas y Maquinaria

> **Monolito Modular · Arquitectura Hexagonal (Puertos y Adaptadores) · Domain-Driven Design (DDD) · Normalización 4FN**  
> Desarrollado con **Java 21**, **Spring Boot 3.5.x**, **Spring Security 6.5 (OAuth2 Resource Server / JWT)**, **MySQL 8.4 (InnoDB, utf8mb4)** y validado mediante **ArchUnit**.

---

## 1. Descripción del Proyecto

**RentaTools** es una plataforma tecnológica diseñada para optimizar, formalizar y asegurar el alquiler de herramientas y maquinaria industrial entre propietarios de equipos (**Proveedores**) y constructores, contratistas o usuarios particulares (**Clientes**).

La plataforma resuelve los problemas más críticos del sector de alquiler:
- **Disponibilidad física en tiempo real**: Control por fichas maestras de herramientas y números de serie físicos específicos (`ToolUnit`), evitando la sobreventa mediante bloqueos pesimistas y consultas de no solapamiento temporal.
- **Seguridad en garantías**: Apertura, cobro, retención y devolución automática de depósitos de seguridad (`SecurityDeposit`) respaldados por transacciones auditadas.
- **Liquidación transparente**: Facturación automatizada al confirmar la reserva (`RENTAL`) y cálculo de mora y cargos por daños al retornar los equipos (`SETTLEMENT`).
- **Inspecciones con evidencia técnica**: Registro fotográfico y checklist de verificación en la entrega (`DELIVERY`) y devolución (`RETURN`).

---

## 2. Tecnologías Utilizadas

| Categoría | Tecnología | Versión | Descripción / Propósito |
|---|---|---|---|
| **Lenguaje** | **Java** | **21 LTS** | Uso exhaustivo de records, pattern matching, `java.time` inmutable y UUID v7. |
| **Framework Base** | **Spring Boot** | **3.5.x** | Inversión de control, inyección por constructor y configuración declarativa. |
| **Seguridad** | **Spring Security** | **6.5** | Stateless, JWT HS256 Nimbus (OAuth2 Resource Server), control RBAC granular con `@PreAuthorize`. |
| **Persistencia** | **Spring Data JPA / Hibernate** | **6.6+** | Mapeo objeto-relacional limpio contra esquema normalizado, optimistic locking con `@Version`. |
| **Motor de Base de Datos** | **MySQL Server** | **8.4 LTS** | Motor transaccional InnoDB, `utf8mb4_0900_ai_ci`, CHECK constraints y claves foráneas estrictas. |
| **Migraciones de BD** | **Flyway** | **11.x** | Control de versiones y evolución reproducible del esquema DDL y datos semilla. |
| **Documentación API** | **Springdoc OpenAPI** | **2.8.x** | Swagger UI interactivo `/swagger-ui.html` y OpenAPI 3.0. |
| **Testing de Arquitectura** | **ArchUnit** | **1.3.x** | Pruebas automáticas de pureza hexagonal y fronteras de módulos sin ciclos. |
| **Testing de Integración** | **Testcontainers** | **1.20.x** | Instancias efímeras reales de MySQL 8.4 en Docker con `@ServiceConnection`. |

---

## 3. Arquitectura y Diseño

### 3.1 Arquitectura Hexagonal y Monolito Modular
El sistema se organiza en módulos autónomos de negocio (*Bounded Contexts*):

```
com.rentatools/
├── shared/           ← Kernel compartido (Money, DateRange, Actor, Ids UUID v7) y eventos de integración
├── iam/              ← Gestión de identidad, perfiles de proveedor y sesiones JWT
├── inventory/        ← Catálogo de herramientas, tarifas y unidades físicas
├── rental/           ← Cotizaciones, reservas, inspecciones físicas y checklists
└── billing/          ← Facturación (RENTAL/SETTLEMENT), cobro de garantías, pagos y reportes
```

Cada contexto sigue estrictamente las capas hexagonales:
- **`domain`** (100% Java puro): Entidades, Raíces de Agregado (`AggregateRoot`), Value Objects inmutables, eventos y excepciones de negocio. **Prohibido importar Spring, Jakarta o Lombok**.
- **`application`** (100% Java puro): Puertos de entrada (casos de uso) y salida (repositorios/gateways), servicios de aplicación orquestadores y DTOs de comando/consulta.
- **`api`** (100% Java puro): Contrato público expuesto para que otros módulos consuman servicios síncronos mediante interfaces y records sin acoplamiento interno.
- **`infrastructure`**: Adaptadores primarios (Controllers REST `@RestController`, Listeners `@EventListener`) y secundarios (JPA Entities, Repositorios Spring Data, BCrypt, JWT Issuer, Local File Storage).

### 3.2 Comunicación entre Módulos sin Ciclos
- **Llamadas síncronas**: Únicamente a través de interfaces `<contexto>.api`, consumidas desde un adaptador de infraestructura que actúa como Capa Anticorrupción (ACL).
- **Eventos de integración asíncronos/síncronos en transacción**: Definidos en `com.rentatools.shared.integration`, permitiendo que un módulo reaccione a cambios sin acoplarse:
  - `ReservationConfirmed` $\rightarrow$ Billing emite factura RENTAL y apertura de garantía.
  - `ReservationClearedForDelivery` $\rightarrow$ Rental cambia estado a `READY_FOR_PICKUP`.
  - `ReservationReturned` $\rightarrow$ Billing genera borrador SETTLEMENT con cálculo de mora.
  - `ReservationSettled` $\rightarrow$ Rental cierra la reserva en `CLOSED`.

### 3.3 Normalización en Cuarta Forma Normal (4FN)
El esquema relacional implementado en `docs/arquitectura/DDL-4FN.md` y ejecutado por Flyway (`V1` a `V4`) respeta rigurosamente las formas normales:
- **1FN / 2FN / 3FN**: Claves primarias claras en formato binario `BINARY(16)` optimizado para UUID v7, sin dependencias parciales ni transitivas.
- **4FN (Eliminación de dependencias multivaluadas independientes)**: 
  - Las tarifas por período (`DAY`, `WEEK`, `MONTH`) se desacoplan de las imágenes y de las categorías (`tool_rates`, `tool_categories`, `tool_images`).
  - Las inspecciones desacoplan el resultado de cada punto del checklist de las fotos asociadas (`inspections`, `inspection_results`, `inspection_photos`).
  - Los estados de las facturas no se almacenan como cadenas redundantes, sino que se derivan de marcas temporales auditadas (`issued_at`, `voided_at`).

---

## 4. Instrucciones de Clonación y Ejecución Rápida

### 4.1 Clonar el Repositorio
```bash
git clone https://github.com/tu-usuario/Proyecto_RentaHerramientas.git
cd Proyecto_RentaHerramientas
```

### 4.2 Despliegue con Docker Compose (Recomendado)
Para compilar y levantar tanto MySQL 8.4 como la aplicación en un solo comando:

```bash
docker compose up -d --build
```

El servidor estará accesible en `http://localhost:8080`.

### 4.3 Ejecución Local con Maven Wrapper
Si prefiere ejecutar la aplicación en su máquina local:

1. **Iniciar la base de datos MySQL 8.4**:
   ```bash
   docker compose up -d mysql
   ```

2. **Arrancar la aplicación en modo desarrollo**:
   ```powershell
   .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
   ```

---

## 5. Usuarios de Prueba Precargados (Swagger UI / Dev)

Para probar la plataforma en **Swagger UI** (`http://localhost:8080/swagger-ui.html`) o **Postman**, se han precargado los siguientes usuarios en la migración `V5__seed_data.sql`:

| Rol | Correo Electrónico | Contraseña | Descripción / Estado |
|---|---|---|---|
| **ADMIN** | `admin@rentatools.com` | `Admin1234!` | Administrador principal del sistema |
| **ADMIN** | `admin.dev@rentatools.com` | `Password123!` | Administrador de desarrollo |
| **PROVEEDOR** | `proveedor1.dev@rentatools.com` | `Password123!` | Herramientas del Norte SAS (Aprobado) |
| **PROVEEDOR** | `proveedor2.dev@rentatools.com` | `Password123!` | Equipos Industriales Sur SAS (Aprobado) |
| **CLIENTE** | `cliente1@rentatools.com` | `Password123!` | Ana María Torres (Cliente Activo) |
| **CLIENTE** | `cliente.dev@rentatools.com` | `Password123!` | Cliente de pruebas Dev |

> [!TIP]
> Para autenticarse en Swagger UI:
> 1. Envíe una solicitud `POST /api/v1/auth/login` con las credenciales elegidas.
> 2. Copie el token del campo `accessToken`.
> 3. Haga clic en el botón verde **Authorize** arriba a la derecha en Swagger UI e introduzca: `Bearer <su_access_token>`.

---

## 6. Documentación de Endpoints REST

La API REST responde bajo el prefijo `/api/v1` y maneja errores estandarizados bajo el formato **RFC 9457 `ProblemDetail`**.

### 6.1 Módulo IAM (Autenticación y Usuarios)

#### Iniciar Sesión
- **Ruta**: `POST /api/v1/auth/login`
- **Permisos**: Público
- **Cuerpo de Solicitud (JSON)**:
  ```json
  {
    "email": "cliente1@rentatools.com",
    "password": "Password123!"
  }
  ```
- **Respuesta Exitosa (200 OK)**:
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "7c9e6679f7d04f...",
    "tokenType": "Bearer",
    "expiresInSeconds": 900
  }
  ```

#### Consultar Perfil Propio
- **Ruta**: `GET /api/v1/users/me`
- **Permisos**: Autenticado (`CLIENTE`, `PROVEEDOR`, `ADMIN`)
- **Cabecera**: `Authorization: Bearer <accessToken>`
- **Respuesta Exitosa (200 OK)**:
  ```json
  {
    "id": "01900000-0000-7000-8000-000000000009",
    "email": "cliente1@rentatools.com",
    "fullName": "Ana María Torres",
    "phone": "+573100000001",
    "status": "ACTIVE",
    "roles": ["CLIENTE"]
  }
  ```

#### Listar Usuarios (Administración)
- **Ruta**: `GET /api/v1/admin/users?page=0&size=10&role=PROVEEDOR&status=ACTIVE`
- **Permisos**: `ROLE_ADMIN`
- **Respuesta Exitosa (200 OK)**:
  ```json
  {
    "items": [
      {
        "id": "01900000-0000-7000-8000-000000000002",
        "email": "proveedor1.dev@rentatools.com",
        "fullName": "Proveedor Uno Dev",
        "roles": ["PROVEEDOR"],
        "status": "ACTIVE",
        "providerProfile": {
          "businessName": "Herramientas del Norte SAS",
          "taxId": "900111222-1",
          "approved": true
        }
      }
    ],
    "page": 0,
    "size": 10,
    "totalElements": 5,
    "totalPages": 1
  }
  ```

---

### 6.2 Módulo BILLING (Facturación y Garantías)

#### Consultar Estado de Facturación de una Reserva
- **Ruta**: `GET /api/v1/billing/reservations/{reservationId}`
- **Permisos**: Partícipes de la reserva (`CLIENTE`, `PROVEEDOR` dueño) o `ADMIN`
- **Respuesta Exitosa (200 OK)**:
  ```json
  {
    "reservationId": "01900000-0000-7000-8400-000000000001",
    "rentalInvoice": {
      "id": "01900000-0000-7000-8600-000000000001",
      "type": "RENTAL",
      "number": 1,
      "status": "ISSUED",
      "subtotal": 100000.00,
      "tax": 18000.00,
      "total": 118000.00,
      "balance": 0.00,
      "paid": true
    },
    "securityDeposit": {
      "id": "01900000-0000-7000-8900-000000000001",
      "requiredAmount": 200000.00,
      "totalCollected": 200000.00,
      "totalRefunded": 0.00,
      "totalRetained": 0.00,
      "balance": 200000.00,
      "settled": false
    }
  }
  ```

#### Registrar Pago de Factura
- **Ruta**: `POST /api/v1/billing/invoices/{invoiceId}/payments`
- **Permisos**: `CLIENTE` (solo método `SIMULATED`), `PROVEEDOR` y `ADMIN` (`CASH`, `CARD`, `TRANSFER`)
- **Cuerpo de Solicitud (JSON)**:
  ```json
  {
    "amount": 118000.00,
    "method": "SIMULATED",
    "reference": "PAY-SIM-7890"
  }
  ```
- **Respuesta Exitosa (200 OK)**: Retorna la factura actualizada reflejando `balance: 0.00` y `paid: true`.

#### Registrar Cobro de Garantía
- **Ruta**: `POST /api/v1/billing/reservations/{reservationId}/deposit/collections`
- **Permisos**: `CLIENTE` (vía pasarela simulada), `PROVEEDOR` o `ADMIN`
- **Cuerpo de Solicitud (JSON)**:
  ```json
  {
    "amount": 200000.00,
    "method": "SIMULATED",
    "reference": "DEP-COL-3456"
  }
  ```
- **Respuesta Exitosa (200 OK)**: Retorna el depósito con `fullyCollected: true` y emite el evento de autorización de entrega si el alquiler ya está saldado.

#### Agregar Cargo por Daño a Factura de Liquidación
- **Ruta**: `POST /api/v1/billing/invoices/{invoiceId}/damage-charges`
- **Permisos**: `PROVEEDOR` dueño de la herramienta o `ADMIN` (solo sobre facturas `SETTLEMENT` en estado `DRAFT`)
- **Cuerpo de Solicitud (JSON)**:
  ```json
  {
    "reservationUnitId": "01900000-0000-7000-8500-000000000016",
    "description": "Reparación de cable de alimentación cortado",
    "amount": 40000.00
  }
  ```

#### Liquidar Definitivamente Reserva y Garantía
- **Ruta**: `POST /api/v1/billing/reservations/{reservationId}/settle`
- **Permisos**: `PROVEEDOR` dueño o `ADMIN`
- **Cuerpo de Solicitud (JSON)**:
  ```json
  {
    "refundMethod": "SIMULATED",
    "refundReference": "REF-DEPOSIT-9901"
  }
  ```
- **Respuesta Exitosa (200 OK)**: Emite la factura `SETTLEMENT`, deduce la retención de la garantía, procesa la devolución del remanente al cliente y emite `ReservationSettled`.

#### Reporte Financiero de Ingresos (Administración)
- **Ruta**: `GET /api/v1/admin/reports/revenue?from=2026-06-01T00:00:00Z&to=2026-06-30T23:59:59Z`
- **Permisos**: `ROLE_ADMIN`
- **Respuesta Exitosa (200 OK)**:
  ```json
  {
    "totalInvoiced": 3156200.00,
    "totalCollected": 3156200.00,
    "depositsRetained": 284360.00,
    "depositsRefunded": 463560.00,
    "breakdownByPaymentMethod": {
      "SIMULATED": 1250000.00,
      "CARD": 850000.00,
      "TRANSFER": 756200.00,
      "CASH": 300000.00
    }
  }
  ```

---

## 7. Pruebas Automatizadas

Para validar que todo el código cumple con los estándares arquitectónicos y de integración:

```powershell
# Ejecutar todas las pruebas unitarias y de integración
.\mvnw.cmd test
```

Para documentación detallada de compilación y perfiles, consulte [docs/COMPILACION.md](file:///c:/Users/kevic/java_proyectos/proyecto_springbot/Proyecto_RentaHerramientas/docs/COMPILACION.md).  
Para procedimientos de despliegue en contenedores o Tomcat nativo, consulte [docs/DESPLIEGUE.md](file:///c:/Users/kevic/java_proyectos/proyecto_springbot/Proyecto_RentaHerramientas/docs/DESPLIEGUE.md).
