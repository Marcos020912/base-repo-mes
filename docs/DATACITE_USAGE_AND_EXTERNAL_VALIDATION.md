# Estadísticas normalizadas y validación del despliegue

## Qué se implementó y qué no se certifica

Se integra el [DataCite Usage Tracker oficial](https://support.datacite.org/docs/datacite-usage-tracker): DataCite procesa los eventos y genera sus informes mensuales. No se convierten los contadores HMAC locales en estadísticas COUNTER. Los totales y series mensuales consultados en la API de DataCite aparecen separados de los contadores locales y se exportan con fuente/fecha/DOI y `certified:false`.

Instalar el tracker o recibir HTTP200 no certifica esta plataforma. Para presentar conformidad oficial se debe confirmar con COUNTER el alcance aplicable y cumplir su proceso de [auditoría independiente](https://www.projectcounter.org/code-of-practice-5-1-0-sections/9-audit/). No se incluye logo ni declaración de plataforma certificada.

## Activación opcional

Por defecto está desactivado. Necesita aprobación institucional para el envío a terceros y un identificador de estadísticas `da-...` proporcionado por DataCite; este identificador **no** es el usuario de la cuenta Repository ni una contraseña.

```properties
repo.metrics.datacite.enabled=true
repo.metrics.datacite.repository-id=da-IDENTIFICADOR_ASIGNADO
# Solo si la integración DOI de esta instalación está preparada para producción:
repo.datacite.api-url=https://api.datacite.org
```

`deploy.sh` permite configurar estas opciones, conserva lo anterior al reutilizar o dejar valores vacíos y elimina propiedades duplicadas antes de escribir. No cambiar a producción una integración DOI de pruebas sin preparar su cuenta/prefijo/credenciales. No basta con habilitar esta opción para crear DOI o certificar estadísticas.

Requisitos de envío:

- Ficha publicada, DOI de producción y configuración habilitada/válida. No DOI Test.
- Sitio HTTPS, DOI `findable` y landing registrada en DataCite exactamente igual a la URL permanente de esta ficha/origen.
- Permiso explícito del visitante (recordado por treinta días). DNT/GPC impiden contactos externos. Retirar permiso detiene eventos futuros; no retira datos ya enviados.
- Vista una vez por carga; descarga solo después de que la transferencia pública de archivo/ZIP termine. Cancelaciones, errores, citas y exportaciones de metadatos no emiten descargas. Preparar un archivo para el navegador no demuestra recepción definitiva en disco.

Antes del permiso no se solicita la biblioteca ni la API de DataCite. Después se consulta el DOI y se carga la biblioteca oficial0.0.5 desde jsDelivr con SRI SHA384. El enlace enviado no tiene query/fragmentos; no se envían cuentas, archivos, nombres de archivo ni JWT de la plataforma. DataCite recibe la conexión/agente del navegador para su procesamiento. Se necesitan conexiones salientes del navegador a `api.datacite.org`, `analytics.datacite.org` y `cdn.jsdelivr.net`.

Una indisponibilidad, bloqueo de red o fallo de integridad no rompe la ficha ni las descargas. No se reenvían automáticamente eventos fallidos desde logs ni se mezclan eventos de máquina con uso humano. El cierre mensual pertenece a DataCite; no afirmar validación real hasta comprobar recepción autorizada y el informe posterior.

## Comprobación externa de solo lectura

Desde una máquina que tenga acceso a la red real, sin modificar el despliegue:

```bash
python3 tools/validation/validate_external.py \
  --base-url https://datos.reduniv.edu.cu \
  --output "evidencia-web-$(date +%Y%m%d-%H%M%S).json"
```

Para ampliar las comprobaciones, añadir los datos reales **públicos**:

```bash
python3 tools/validation/validate_external.py \
  --base-url https://DOMINIO_REAL \
  --dataset-id ID_PUBLICADO --doi DOI_DE_ESTA_VERSION \
  --repository-id da-IDENTIFICADOR_ASIGNADO \
  --smtp-host NOMBRE_DEL_CERTIFICADO --smtp-port 25 --smtp-mode starttls \
  --output "evidencia-completa-$(date +%Y%m%d-%H%M%S).json"
```

Para una CA institucional privada, añadir `--ca-file /ruta/ca-institucional.pem`. No hay opción de ignorar certificados: también se verifica el nombre del servidor. La conexión SMTP no inicia sesión ni envía correo; por tanto no prueba entrega ni credenciales.

Se comprueban página HTTPS/TLS, preflight CORS del registro, landing y metadatos públicos, DOI `findable`/landing registrados, integridad de la biblioteca y disponibilidad de la recepción del tracker, según opciones elegidas. Sin cabeceras CORS para una petición del mismo origen se marca inconcluso, no se diagnostica un fallo; confirmar registro real en navegador. No se siguen redirecciones automáticamente. No se crean cuentas, modifican datasets, acuñan DOI ni envían eventos de uso. El agente del comprobador se identifica como robot para evitar contaminar contadores locales.

El JSON tiene permisos0600, no sobrescribe evidencia existente ni guarda cuerpos crudos/cookies/credenciales. El resultado corresponde **solo al scope listado**:

- salida0: comprobaciones seleccionadas aprobadas;
- salida1: alguna comprobación falló;
- salida2: sin fallos, pero evidencia inconclusa (por ejemplo recepción mensual sin confirmar).

HTTP200 en `/api/check` no acredita por sí solo un evento ni un informe mensual: se marca inconcluso. Tampoco se declara la plataforma certificada. Si el dominio es institucional, ejecutar desde la VM/red institucional y también desde otra red autorizada para distinguir alcance DNS/firewall.

## Evidencia humana pendiente (no marcar sin realizarla)

- [ ] Infraestructura confirma dominio/DNS, acceso público o institucional, HAProxy/TLS y CA SMTP.
- [ ] Operador guarda JSON de comprobaciones realizadas en la VM y desde una red externa autorizada.
- [ ] Cuenta DataCite de producción y DOI real comprobados; workflow Test→staging aprobado antes de operaciones reales.
- [ ] Institución aprueba política de estadísticas/transferencia a terceros y obtiene `data-repoid`.
- [ ] Se observa recepción de vista/descarga aprobadas y se verifica informe del mes siguiente. Usar los [procedimientos de DataCite](https://support.datacite.org/docs/datacite-usage-tracker#testing).
- [ ] Se confirma entrega de un correo de verificación a una cuenta de prueba autorizada (no lo hace el script).
- [ ] Revisión manual de teclado/lector de pantalla y estilos bibliotecarios/importación institucional.
- [ ] Responsable registra auditoría/alcance y evidencia de certificación si corresponde; hasta entonces `certified:false`.

## Pruebas locales reproducibles

```bash
python3 -m unittest discover -s tools/validation -p 'test_*.py'
python3 -m unittest discover -s tools/security -p 'test_*.py'
node tools/e2e/datacite-usage-consent.cjs
```

Las pruebas HTTPS usan un certificado temporal generado localmente: verifican CA/nombre sin relajar TLS. El fixture virtual HTTPS del tracker no contacta servicios reales y comprueba consentimiento/revocación, DNT, DOI Test, landing ajena, fallos, exportación y ausencia de solicitudes por cambio de idioma. No sustituye prueba mensual ni auditoría externa.

## Comprobación pública realizada el 9 de octubre de 2026

Desde este entorno, `https://datos.reduniv.edu.cu/login.html` respondióHTTP200, TLS validado y firma de la pantalla de acceso real. El OPTIONS del registro devolvió200 sin cabeceras CORS: resultado inconcluso de esa comprobación de mismo origen, no evidencia de error de registro. Informe `/tmp/reduniv-external-readonly-2026-10-09-v2.json`, salida2, cero fallos y una comprobación inconclusa. No se probó SMTP real, no se crearon usuarios, no se emitieron eventos ni se modificó el servidor. Resultado puntual, no garantía permanente de disponibilidad ni de que el desarrollo esté desplegado.
