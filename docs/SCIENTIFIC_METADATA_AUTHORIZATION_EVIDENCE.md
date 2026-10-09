# Evidencia de permisos de metadatos científicos

Rama de desarrollo; 2026-10-08. Esta evidencia no declara auditados todos los endpoints.

## Controladores examinados

`ScientificRelationController`, `ScientificFundingController` y
`ScientificCreatorController`, operaciones `list` y `replace`.

| Actor | Lectura PUBLISHED | Lectura otros estados | Escritura DRAFT | Escritura otros estados |
|---|---|---|---|---|
| Anónimo | Sí en controlador* | 404 | 403 | 403 |
| Usuario ajeno | Sí | 404 | 403 | 403 |
| Autor USER/CURATOR/ADMINISTRATOR | Sí | Sí | Sí | 409 |
| Curador/administrador ajeno | Sí | Sí, ruta editorial | 403 | 403 |

*La disponibilidad HTTP de rutas científicas depende además de `WebSecurityConfig`.
El endpoint público dedicado no es equivalente a una ruta editorial autenticada.
La lectura privada por curadores permite revisión, **no edición de metadatos ajenos**.
Las versiones RESTRICTED y WITHDRAWN tampoco son editables por estos controladores.

## Prueba reproducible

`ScientificMetadataAuthorizationMatrixTest`: 35 combinaciones (cinco estados ×
siete identidades/roles/autorías), seis métodos: 210 casos. Todos los rechazos
comprueban ausencia de interacción con repositorio de datos y de evento de cambio.
Las lecturas no generan eventos de modificación. Las escrituras admitidas crean evento.

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew --offline --no-daemon test \
  --tests '*ScientificMetadataAuthorizationMatrixTest' \
  --tests '*ScientificCitationControllerTest'
```

## Límites y siguientes controles

No cubre filtros JWT/verificación/rate limiting, carreras transaccionales, endpoints
legacy de recursos/archivos, Markdown/ZIP, DOI, retirada, perfiles, privacidad ni
administración. Sus pruebas específicas y el E2E real complementan esta matriz;
no permiten cerrar S04/S06/F15 globalmente. La matriz HTTP de esos endpoints y
la concurrencia publicación-escritura siguen pendientes.

## Guard de lectura de rutas legacy

`ScientificContentReadGuardTest`: 600 combinaciones (cinco estados × cuatro
políticas OPEN/RESTRICTED/embargo futuro/expirado × GET/HEAD × ficha/data/archive
× cinco actores). Una ACL legacy pública no basta para leer fichas o contenido
no publicados: solo autor, curador o administrador pueden hacerlo. En PUBLISHED
los metadatos siguen públicos; los archivos respetan acceso/embargo. El guard
no reemplaza ACL upstream: aun si permite continuar, el servicio aplica sus ACL.

`DatasetArchiveControllerTest` verifica paginación de 101 archivos, ZIP legible
con contenido exacto, y rechazo de traversal antes de comprometer headers.

Suite Java completa ampliada: 55 suites, 1318 casos, sin fallos/errores/omitidos;
`/tmp/reduniv-expanded-full-java.log`. La matriz HTTP ampliada y validación ZIP
con parser independiente están en el E2E real, no solo en mocks. Su resultado
se registra separadamente en el plan.

### Riesgo concurrente aún pendiente

Los controladores de tablas separadas comprueban DRAFT antes de escribir, pero
no bloquean ni incrementan la versión de ScientificRecord. La matriz secuencial
no prueba serialización con envío/publicación. Falta prueba de carrera y estrategia
coordinada de bloqueo, incluyendo archivos legacy; F15 sigue abierto.
