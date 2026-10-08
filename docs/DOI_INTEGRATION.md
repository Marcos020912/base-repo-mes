# Integración DOI con DataCite

La especificación institucional del 28-09-2026 indica usar la API REST de
DataCite, no desplegar un servidor DOI. Diseño contrastado con las guías
oficiales de [creación](https://support.datacite.org/docs/api-create-dois),
[actualización](https://support.datacite.org/docs/updating-metadata-with-the-rest-api)
y [estados](https://support.datacite.org/docs/doi-states).

## Flujo implementado en `develop-reduniv`

1. Autor prepara el depósito y pulsa **Reservar DOI**. El backend guarda primero
   la intención en PostgreSQL y reserva dos identificadores Draft: uno conceptual
   para el conjunto y otro para la versión. La operación puede repetirse.
2. En Draft, el DOI aparece como **reservado, no público**. No se enlaza a doi.org
   ni se incluye en la cita definitiva. El autor envía el depósito a revisión.
3. Curación revisa y publica. Se verifican metadatos obligatorios y se envían
   a DataCite con `event=publish`; la aplicación comprueba `findable` para ambos
   DOI antes de cambiar el estado local a `PUBLISHED`. Se incluyen título,
   autoría, editorial, año, tipo y URL; cuando constan, también licencia,
   disciplina, palabras clave, institución/ROR y ORCID. La ficha admite
   identidades por creador: el ORCID se asigna al autor correspondiente por
   `creatorId`, y sus afiliaciones se envían en el orden configurado, con ROR
   cuando consta. Si no hay identidades por autor, el campo ORCID heredado
   del dataset se usa únicamente cuando existe un solo creador, para no
   atribuirlo erróneamente a coautores. Un ORCID declarado no implica que se
   haya autenticado por OAuth ni que la identidad personal esté contrastada.
4. El DOI de versión apunta a `https://<dominio>/datasets/<id>` y el conceptual
   a `/datasets/<id-raíz>/concept`, que redirige a la versión publicada más
   reciente. Los DOI de versión incluyen `IsVersionOf` y, si corresponde,
   `IsNewVersionOf`; el conceptual incluye `HasVersion`.
5. La versión publicada es inmutable. Si se retira, la landing pública conserva
   el aviso/tombstone; no se intenta borrar un DOI Findable.

Antes de reservar, se comprueba que la URL base pública configurada sea HTTPS.
La disponibilidad real de esa ruta y su resolución desde Internet deben
verificarse en el entorno institucional; la aplicación no puede garantizarlo
solo por la sintaxis de la URL.

El servicio registra el último estado y eventos de sincronización en
`doi_registrations` y `doi_sync_events`. Una intención local se confirma antes
de llamar a DataCite. Tras un fallo se puede repetir **Reservar** o **Publicar**:
el backend consulta el identificador conocido y continúa sin generar otro.
Para hacer esto seguro, el sufijo se deriva de un UUID estable del recurso en
vez de pedir un sufijo aleatorio a DataCite; esta es una excepción deliberada
a la recomendación no obligatoria del documento institucional. La configuración
del prefijo o del entorno Test/Production no puede cambiarse para una reserva
existente sin conciliación explícita.

## Configuración

`sudo ./deploy.sh` puede solicitar estos datos al reconfigurar la instalación.
Si se reutiliza la configuración previa, no vuelve a pedirlos. Las contraseñas
se introducen sin eco y el archivo local permanece con permiso `600`.

```properties
repo.datacite.enabled=false
repo.datacite.api-url=https://api.test.datacite.org
repo.datacite.repository-id=<cuenta Repository de Test>
repo.datacite.password=<secreto entregado por canal seguro>
repo.datacite.prefix=<prefijo de Test>
repo.datacite.public-base-url=https://datos.reduniv.edu.cu
```

DataCite Test y Production necesitan credenciales/prefijos distintos. El
cliente solo acepta sus hosts oficiales por HTTPS. No guardar secretos en Git,
en capturas o en el frontend. **Por defecto está desactivado.** Con la opción
desactivada continúa el flujo manual de DOI preexistente; con la opción activa
la publicación manual se bloquea y se usa únicamente la publicación DataCite.

## Antes de producción

- Aplicar la migración SQL tras copia de seguridad y validar restauración.
- Obtener cuenta Repository, prefijo y credenciales de DataCite Test por canal
  seguro. Probar reserva, publicación, resolución, actualización, fallos de red
  y relaciones en Test y staging. Repetir la validación con Production antes de
  habilitarlo para usuarios.
- Asegurar que el dominio público y TLS del HAProxy sirvan realmente las rutas
  `/datasets/...`; el DOI no debe apuntar a una URL inexistente o temporal.
- Los DOI manuales ya asignados no se convierten automáticamente. Conciliarlos
  con DataCite y definir migración antes de activar el modo automático para
  esos depósitos. No intercambiar las credenciales de Test y Production sobre
  registros ya reservados.
- Revisar metadatos y relaciones con bibliotecarios. Las pruebas simuladas y la
  suite local no sustituyen la validación contra DataCite real.

### Mantenimiento de landing pages tras un cambio de dominio

Operación exclusiva de administrador para versiones publicadas gestionadas por
la integración automática (no adopta DOI manuales ni cambia de entorno Test a
Production). Configure primero `repo.datacite.public-base-url` con el origen
HTTPS aprobado y compruebe DNS, certificado y rutas antes de actualizar DataCite.

1. `GET /api/v1/scientific/{id}/doi/landing-targets` devuelve los DOI y URLs
   conceptual/de versión previstos.
2. Revise ambos destinos. Envíe exactamente ese JSON a
   `POST /api/v1/scientific/{id}/doi/refresh-urls` con autenticación de admin.
3. Consulte el historial DOI. Cada actualización registra éxito/error; también
   queda un evento editorial con el actor. No modifica archivos ni contenido.

No se aceptan URLs arbitrarias del cliente. Si la configuración cambia entre
la vista previa y la confirmación, se rechaza la operación. Si solo una de las
actualizaciones remotas termina, reintente con los mismos destinos: las
actualizaciones son idempotentes. No se afirma atomicidad entre DataCite y la
base local, ni se dan las pruebas simuladas por validación institucional.

La publicación de una sucesora sincroniza `IsPreviousVersionOf` en el DOI de su
predecesora automática, además de `IsNewVersionOf` en la nueva versión y las
relaciones conceptuales. Actualiza solo `relatedIdentifiers`, conservando las
relaciones científicas locales y sucesores publicados/retirados conocidos. La
base local es la fuente de estas relaciones: cambios manuales externos necesitan
conciliación previa. Si falla la sincronización, la nueva versión permanece en
revisión y la publicación puede reintentarse, aunque DataCite ya haya hecho
Findable alguno de los DOI. No se promete una transacción distribuida.
