# Auditoría parcial de cierre RedUniv — 2026-10-08

**Resultado: objetivo completo NO demostrado.** Este documento no sustituye
el informe, prototipo HTML ni especificación DOI del usuario. No autoriza
merge, push o despliegue; el trabajo permanece en `develop-reduniv`.

## Actualización: fuentes recuperadas el 2026-10-08

El usuario volvió a aportar los tres documentos sin sufijos `(1)`. Se han leído
y registrado sus SHA-256 en [la matriz de requisitos](REDUNIV_SOURCE_REQUIREMENTS.md).
La ausencia de fuentes descrita abajo corresponde a la auditoría anterior, no
al estado actual. El objetivo sigue sin cierre: la matriz detecta faltantes
adicionales de implementación, no solo validaciones institucionales.

## Fuentes y límites (estado histórico anterior)

Se comprobaron nuevamente estas rutas; ninguna está disponible:

- `/home/marcos/Descargas/prototipo-repositorio-cientifico(1).html`
- `/home/marcos/Descargas/INFORME_MEJORAS_REPOSITORIO_CIENTIFICO-1.pdf`
- `/home/marcos/Descargas/Integracion_DOI_Repositorio_RedUniv(1).md`

Sin sus contenidos no puede afirmarse una correspondencia completa entre
los requisitos originales y el código. Se solicitaron los adjuntos al usuario.
El checklist y roadmap son seguimiento interno, no evidencia independiente de
que se haya conservado el alcance original.

## Evidencia local reciente y su alcance

- `daa28fc`: E2E Chrome/JAR/PostgreSQL18/SMTP loopback aprobado, incluyendo
  asistente, curación, paquetes de preservación, revisión externa/revocación,
  catálogo multiusuario y descarga pública directa a OPFS. Selector simulado;
  no implica disco personal, diálogo nativo ni DataCite real.
- `0a79657`, `cc068dc`: monitor HTTP/Chrome prueba 16 MiB exactos sin Blob,
  fallo de escritura con cierre HTTP y preservación del archivo anterior,
  cancelación, panel móvil 320 px con CSS real y axe sin infracciones.
  No demuestra rendimiento masivo ni WCAG manual.
- En esta auditoría se ejecutaron las seis pruebas de
  `DataCiteMetadataMapperTest`: todas aprobadas. Comprueban campos obligatorios,
  ORCID/afiliaciones por autor y financiación; no se conectan a DataCite.
- El checklist conserva evidencia de 457 pruebas Java y restauración sintética
  anteriores. No convertir ese registro histórico en una afirmación de suite
  completa nuevamente ejecutada en esta auditoría.

## Pendientes registrados — ninguno cerrado por esta auditoría

Cada entrada siguiente sigue **incompleta o sin verificar en su alcance real**.
No reemplazar la validación solicitada por un fixture sintético equivalente.

### 1. Pendiente

Validar migración/retroceso con copia **real** de PostgreSQL y archivos de staging, medir tiempos y arrancar la aplicación restaurada.

### 2. Pendiente

Inventariar `contentUri` heredados que apunten fuera de `repo.basepath` ejecutando la herramienta sobre copia real de staging; migrar archivos o documentar una solución de almacenamiento aprobada antes de activar las descargas restringidas por ruta.

### 3. Pendiente

Probar funcionalmente con usuarios reales y revisar accesibilidad WCAG 2.2 AA.

### 4. Pendiente

Completar pruebas del asistente con usuarios en staging: fallos de red o almacenamiento reales a mitad de subida, nueva versión con datos reales y comportamiento con PostgreSQL/HAProxy. La prueba local automatizada con Chrome y H2 ya cubre cargas Markdown/ZIP, validación, envío, reparación de un fallo de red simulado y derivación de versión sintética.

### 5. Pendiente

Rotar credenciales/JWT de producción y revisar logs históricos; pruebas de seguridad y de carga del limitador con varias instancias y HAProxy realmente desplegado. Restringir acceso directo al backend.

### 6. Pendiente

Probar con revisores externos el acceso temporal detrás de HAProxy, políticas de logs/caché y pruebas de concurrencia editorial.

### 7. Pendiente

Validar el flujo DOI real con cuenta Repository, prefijo y credenciales de DataCite Test/Production; probar resolución pública y conciliar DOI manuales existentes antes de activar el modo automático en producción.

### 8. Pendiente

Validar ORCID OAuth con credenciales Sandbox reales y usuarios de prueba, registrar el callback HTTPS institucional y acordar política para coautores que no pueden autenticarse con la cuenta del depositante. La búsqueda ORCID por nombre no debe utilizarse para atribuir identidad; ROR necesita salida a Internet desde la VM o un proxy institucional.

### 9. Pendiente

Aprobar vocabularios institucionales de licencia/disciplinas y activar `repo.scientific.strict-vocabulary=true` después de migrar valores heredados; las listas actuales no son un catálogo institucional aprobado.

### 10. Pendiente

Validación bibliotecaria de todos los estilos de cita y del orden de autoría heredado; los nuevos formatos aún son preliminares.

### 11. Pendiente

Validar rendimiento de facetas de proyecto/financiador y otras consultas agregadas con volumen real en PostgreSQL; validar valores institucionales de proyectos/financiadores y metadatos de financiación en DataCite Test.

### 12. Pendiente

Dimensionar I/O y pool JDBC para la auditoría, configurar/validar una dirección institucional real para las alertas y definir política de archivos anteriores sin huella.

### 13. Pendiente

Validar RO-Crate con curadores externos, aportar URL/ROR/contacto institucional aprobados y completar perfiles OAIS SIP/AIP/DIP y cobertura de procedencia de cargas legadas. Validar la proyección PROV-O con consumidores externos y paquetes de preservación y procedencia en almacenamiento externo, firmar manifiestos si la institución lo requiere e internacionalizar la UI.

### 14. Pendiente

Validar este flujo con archivos grandes y endpoints públicos reales en staging; el modo compatible conserva el archivo en memoria; el modo opt-in directo a disco requiere navegador compatible y validación de selección nativa/volumen real.

## Implementación pendiente frente a validación externa

La internacionalización de la UI y los perfiles institucionales OAIS no deben
etiquetarse únicamente como «pruebas de producción pendientes»: el checklist
incluye trabajo aún no definido/completado. La interfaz inspeccionada mantiene
textos en español; falta acordar idiomas, alcance de traducción y criterio de
aceptación. Asimismo, el paquete BagIt/RO-Crate/PROV implementado no acredita
por sí solo un perfil institucional SIP/AIP/DIP.

## Condición de cierre

1. Recuperar documentos originales y construir trazabilidad por requisito.
2. Separar requisitos locales, decisiones institucionales y pruebas externas
   sin eliminar ninguno del alcance pedido.
3. Implementar faltantes confirmados y reunir evidencia con alcance adecuado.
4. Revisar cada requisito original antes de declarar el objetivo completo.

No se considera alcanzado el objetivo, ni se propone fusionar ramas.
