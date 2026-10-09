# Uso local y expediente interno

## Métricas de uso

Catálogo y fichas públicas muestran consultas y descargas de los últimos 30 días.
Operaciones permite a administradores y curadores consultar hasta 90 días y
exportar un informe JSON con desglose por versión, fecha UTC y evento.

Se cuentan GET públicos exitosos (200/206) de versiones actualmente PUBLISHED.
Se excluyen HEAD, errores, solicitudes inline, precargas y agentes robot conocidos.
Solicitudes del mismo enlace y visitante técnico se agrupan en una ventana deslizante
de 30 segundos. Un visitante técnico no equivale a una persona: redes compartidas,
agentes similares y automatización no reconocida afectan los resultados.
Descarga significa respuesta servida, no recepción íntegra confirmada por el cliente.

No se almacenan IP, User-Agent, usuario ni ruta de archivo originales. La clave
HMAC incluye día UTC, recurso, evento, enlace, dirección y agente. Se conserva un
máximo de 90 días mediante purga diaria; el informe excluye días más antiguos incluso
antes de la purga. No son totales históricos ni estadísticas certificadas COUNTER;
estos contadores locales no se envían a DataCite. La integración opcional y separada
con su Usage Tracker, con consentimiento explícito, se documenta en
[DATACITE_USAGE_AND_EXTERNAL_VALIDATION.md](DATACITE_USAGE_AND_EXTERNAL_VALIDATION.md).

Configuración opcional:

```properties
repo.metrics.usage-enabled=true
# Secreto privado de al menos 32 caracteres. No subir al repositorio.
# repo.metrics.privacy-secret=
```

Sin secreto propio se utiliza `repo.auth.jwtSecret`; si no alcanza 32 caracteres,
la recogida se desactiva. Cambiar el secreto puede alterar deduplicación del día.
Tras desactivar recogida, los agregados existentes siguen visibles hasta caducar.
Con HAProxy, utilizar únicamente configuración de proxies confiables; no confiar
ciegamente en cabeceras de dirección enviadas por el cliente.

## Autoevaluación interna

Desde Operaciones se abre `self-assessment.html`. Curadores y administradores pueden
consultar y exportar el expediente; solo administradores modifican sus 16 requisitos.
Cada requisito registra responsable, declaración, enlaces HTTPS, estado, revisión,
fecha y autor de actualización. Guardados obsoletos devuelven conflicto: recargar
antes de reintentar. Los estados preparados/revisados requieren responsable,
declaración y al menos una evidencia. No se aceptan credenciales o query strings en
enlaces; no incorporar información confidencial en declaraciones ni URLs.

Referencia: [CoreTrustSeal Requirements 2026–2028 v01.00](https://zenodo.org/records/17660463).
El estado «revisada internamente» no implica validación externa: el informe siempre
incluye `certified: false`. La aprobación de políticas, preparación de evidencias y
solicitud de certificación siguen siendo responsabilidades institucionales.

## Migración

`docs/migrations/2026-09-scientific-records.sql` añade las tablas de observaciones y
autoevaluación de forma idempotente, sin borrar ni recrear datasets/usuarios.
Aplicar con el procedimiento habitual de actualización y copia de seguridad.
