# Preparación y paso a producción — Datos RedUniv

**Candidato, no autorización de publicación.** Los cambios permanecen en
`develop-reduniv`. No se ha fusionado, creado tag público ni actualizado la VM.
La demo local y sus datos no son configuración de producción.

## 1. Aprobación y respaldo

1. Aprobar PR y notas de versión; después crear un tag estable `vX.Y.Z`.
2. Reservar mantenimiento. Detener escrituras y obtener `pg_dump -Fc`, copia
   consistente de `/var/lib/base-repo/data` y configuración externa.
3. Probar restauración aislada según `tools/releases/README.md`. No ejecutar
   DROP ni recrear PostgreSQL para actualizar.
4. Revisar `docs/migrations/2026-09-scientific-records.sql` contra el esquema real;
   aplicar migraciones aprobadas y usar `spring.jpa.hibernate.ddl-auto=validate`.
   `update.sh` bloquea otros modos. Para primera instalación, `deploy.sh` admite
   `update` con advertencia: preparar esquema antes de abrir el acceso público.

## 2. Configuración externa de la VM

Mantener `config/application.properties` **fuera de Git** y con acceso restringido.
No copiar `.local-runtime/`, credenciales, datos de demo ni logs a una Release.
Comprobar cada clave una sola vez:

- `repo.auth.enabled=true`, secreto JWT único >=32 bytes, sin clave admin conocida.
- PostgreSQL real, `repo.basepath=file:/var/lib/base-repo/data/` persistente y
  escribible por la cuenta del servicio. La plantilla sola no basta.
- Dominio real acordado (históricamente `datos.reduniv.edu.cu`), puerto interno,
  IP privada de VM y red exacta del HAProxy. No asumir que DNS/SSL están operativos.
- `repo.security.allowedOriginPattern=https://DOMINIO` (incluir puerto si no443),
  `repo.public-domain=DOMINIO`, `server.forward-headers-strategy=framework`.
- SMTP real, sin captura local. STARTTLS obligatorio o TLS implícito y validación
  de nombre de certificado activada. No usar `ssl.trust=*` ni desactivar identidad.
  Con certificado institucional, instalar la CA aprobada o confianza limitada al
  host configurado; el nombre SMTP debe estar en el certificado. Si el DNS apunta
  a otra IP, infraestructura debe corregirlo o confirmar una entrada hosts, sin
  sustituir el nombre por una IP que el certificado no incluye.
- `repo.privacy.require-assessment=true`.
- `management.endpoints.web.exposure.include=health`: no exponer todos los Actuator.
- Elasticsearch interno accesible solo por la app/operación autorizada. Mantener
  datos/índices existentes; no desactivar autenticación de un clúster compartido.
- Configurar DOI institucional aprobado si se va a registrar DOI reales;
  estadísticas locales son la opción acordada. No activar servicios de prueba.

Comprobación sin cambios ni salida de secretos:

```bash
python3 tools/releases/production_preflight.py \
  --config config/application.properties --strict-schema
```

Un resultado válido no verifica conexiones ni credenciales; tampoco inspecciona
cuentas ya guardadas. Rotar claves históricamente expuestas mediante procedimiento
separado. La protección de ramas/repositorio requiere revisión en GitHub.

## 3. HAProxy remoto

TLS termina en HAProxy; app escucha HTTP **interno**, no habilitar HTTPS en Java
por duplicado. Aplicar `haproxy-base-repo.cfg` al backend del frontend acordado.
Sobrescribir `Forwarded` y `X-Forwarded-*`, no confiar en cabeceras arbitrarias del
cliente. Limitar puerto de app a HAProxy mediante firewall; PostgreSQL/ES no deben
ser públicos. Validar rutas de subida, límites y timeouts para ZIP grandes.
Probar la salud interna y la URL HTTPS externa, no solo localhost.

## 4. Instalar o actualizar

Primera instalación/reinicio supervisado:

```bash
sudo ./deploy.sh
```

PostgreSQL y Elasticsearch siguen opcionales; responder No al aprovisionamiento
si existen. Reutilizar configuración no la reescribe. El preflight falla **antes
 de parar Java** ante claves duplicadas o ajustes inseguros. La compilación se
hace en staging, sin sobrescribir el JAR activo. Solo se detiene el Java de esta
instalación. HTTP200 debe pertenecer al PID candidato antes de anunciar éxito.

Actualización desde código (requiere JDK21, acceso a Gradle y dependencias):

```bash
sudo ./update.sh vX.Y.Z
# Sin argumento usa main; no representa por sí solo una Release aprobada.
```

Actualiza mediante fast-forward y ejecuta deploy.sh en modo reconstrucción,
reutilizando configuración sin instalar/configurar PostgreSQL, Elasticsearch ni
firewall. Confirme respaldo comprobado; no existe rollback automático. No ejecutar
este flujo en el clon de desarrollo para fusionar ramas.

Alternativa desde Release aprobada, sin descargar Gradle en VM:

```bash
sudo ./update.sh vX.Y.Z /ruta/candidato.jar SHA256_DE_RELEASE
```

La configuración y JAR previo se respaldan en `.releases` privado. El preflight y
checksum se comprueban antes de cambiar servicio. No hay rollback automático de
esquema, instalación automática de certificados ni emisión de DOI de prueba.

`deploy.sh`/`update.sh` conservan el modo nohup existente: **no equivalen a un
servicio systemd con reinicio automático**. En producción, infraestructura debe
registrar Java como servicio bajo una cuenta no root, con acceso al JAR/config,
log en journal, directorio persistente y Restart=on-failure. No arrancar nohup y
systemd simultáneamente; migrar el supervisor durante mantenimiento controlado.

## 5. Aceptación antes de abrir acceso

- Login propio, cuenta suspendida/restringida, registro y correo de verificación
  recibido de verdad (el sink local no demuestra entrega institucional).
- Autor crea borrador; usuario ajeno/admin no lo edita; admin modera/elimina según
  reglas. Probar privacidad/restricción, envío, devolución y publicación.
- Subir `description.md`, imágenes auxiliares, tablas, PDF en tipo apropiado;
  ZIP integral, archivo con espacios, descarga individual/completa, reemplazo y
  versión. El mensaje genérico de error no certifica la causa de fallo.
- Catálogo/ES, cola durable y recuperación del índice; probar parada temporal deES.
- DOI real solo bajo aceptación institucional, evitando registros duplicados.
- Restauración consistente, disco/cuotas, monitorización, copias programadas,
  recuperación tras reinicio VM, pruebas de carga y revisión de dependencias/CVE.
- Revisión humana de accesibilidad y aprobación de datos/políticas/licencias.

**No afirmar producción validada hasta completar estas pruebas en la VM/HAProxy.**
