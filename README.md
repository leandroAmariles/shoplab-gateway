# shoplab-gateway

Puerta única de la API de ShopLab Helpdesk. Hoy reenvía todo al monolito; cada servicio que se extrae se lleva su
prefijo cambiando solo configuración (patrón *strangler fig*, LAB-107). Spring Cloud Gateway sobre WebFlux, sin estado.

La especificación, el plan y los contratos están en `shoplab-helpdesk/specs/LAB-107-strangler-gateway/`.

## Rutas

| Nombre | Prefijo | Destino por defecto |
|---|---|---|
| `chat` | `/api/chat` | monolito |
| `documents` | `/api/documents` | monolito |
| `knowledge` | `/api/knowledge` | monolito |
| `eval` | `/api/eval` | monolito |
| `assistant` | `/api/assistant` | monolito |

Coinciden la ruta exacta y sus subrutas. Lo demás responde 404. No se reescribe nada: método, ruta, consulta, cuerpo,
`Host` y credenciales llegan como los envió el cliente. Las cabeceras `Forwarded` y `X-Forwarded-*` de un remitente no
confiable se descartan.

## Configuración

| Variable | Por defecto | Efecto |
|---|---|---|
| `GATEWAY_MONOLITH_URI` | `http://localhost:8081` | destino de las rutas no movidas |
| `SHOPLAB_GATEWAY_ROUTES_<NOMBRE>_URI` | monolito | mueve esa ruta |
| `SHOPLAB_GATEWAY_ROUTES_<NOMBRE>_PREFIX` | — | solo para una ruta nueva |
| `SHOPLAB_GATEWAY_ROUTES_<NOMBRE>_RESPONSETIMEOUT` | `120s` | espera máxima hasta las cabeceras de respuesta |
| `SERVER_PORT` | `8080` | puerto del gateway |

```bash
# Mover la base de conocimiento a su servicio
SHOPLAB_GATEWAY_ROUTES_DOCUMENTS_URI=http://retrieval-service:8080
SHOPLAB_GATEWAY_ROUTES_KNOWLEDGE_URI=http://retrieval-service:8080

# Dar de alta un servicio nuevo
SHOPLAB_GATEWAY_ROUTES_CATALOG_PREFIX=/api/catalog
SHOPLAB_GATEWAY_ROUTES_CATALOG_URI=http://catalog-service:8080
```

Revertir es quitar la variable. Una configuración inválida impide el arranque y nombra la ruta.

## Errores propios

| Código | HTTP | Cuándo |
|---|---|---|
| `SLHD_GATE_00001` | 404 | sin ruta |
| `SLHD_GATE_00002` | 503 | destino inalcanzable |
| `SLHD_GATE_00003` | 504 | el destino no respondió a tiempo |
| `SLHD_GATE_00004` | 500 u otro | fallo inesperado |

Los errores del destino pasan tal cual.

## Desarrollo

```bash
./mvnw verify                                              # pruebas, cobertura (80 %), Checkstyle y PMD
GATEWAY_MONOLITH_URI=http://localhost:8081 ./mvnw spring-boot:run
```

Gestión: `/actuator/health` (con `liveness` y `readiness`), `/actuator/info`, `/actuator/prometheus`.

## Publicación

`release-please` abre el PR de versión; al fusionarlo crea el tag `vX.Y.Z`, y `release.yml` publica
`ghcr.io/leandroamariles/gateway` y actualiza `apps/gateway/overlays/dev` en `shoplab-infra`. Necesita los secretos
`RELEASE_PLEASE_TOKEN` y `DEPLOY_APP_PRIVATE_KEY` y la variable `DEPLOY_APP_ID`.
