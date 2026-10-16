# Colecciones Postman — RentaTools

## Importación en Postman
1. Importa el entorno `postman/RentaTools.dev.postman_environment.json` y selecciónalo como entorno activo (**RentaTools Dev**).
2. Importa las colecciones del directorio `postman/`:
   - `10-iam.postman_collection.json`
   - `20-inventory.postman_collection.json` (paso B2)
   - `30-rental.postman_collection.json` (pasos B4–B5)
   - `40-billing.postman_collection.json` (paso A5)
   - `00-flujo-completo.postman_collection.json` (paso A6)

## Ejecución local
```powershell
docker compose up -d
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
npx newman run postman/10-iam.postman_collection.json -e postman/RentaTools.dev.postman_environment.json
```
