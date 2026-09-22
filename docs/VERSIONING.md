# Política de versiones y despliegue

## Ramas y versiones
- main: código integrado; producción se despliega desde un tag, no desde main.
- feature/* y fix/*: cambios mediante pull request, revisión y pruebas.
- Tags inmutables vMAJOR.MINOR.PATCH: cambio incompatible, funcionalidad compatible,
  corrección compatible respectivamente. No sobrescribir versiones publicadas.
- Cada GitHub Release debe incluir notas, requisitos Java, cambios de esquema,
  instrucciones de migración/retorno, bootJar compilado desde el tag y SHA-256.
- Una versión no está validada hasta probar login, verificación por correo,
  permisos, carga, búsqueda y descarga.
- No versionar secretos, configuración de VM, bases ni archivos de usuarios.
- Configurar protección de main y revisión obligatoria en GitHub. Esta política
  no implica que esas restricciones estén activadas técnicamente.

## Operación
deploy.sh pregunta por PostgreSQL y Elasticsearch (No por defecto).
Reutilizar configuración conserva el archivo sin reescribirlo ni tocar UFW.
El aprovisionamiento PostgreSQL solo crea recursos ausentes; no ejecuta DROP.
No confundir reiniciar servicios con borrar datos. Hibernate también puede
modificar el esquema: revisar ddl-auto y migraciones antes de actualizar.

## Actualización manual (no CI/CD alojado)
1. Crear tag/release después de aprobar las pruebas. Compilar en una máquina con
   acceso a dependencias: ./gradlew --no-daemon -Dprofile=minimal bootJar.
2. Transferir el JAR del tag a una ruta fuera de build/libs. Verificar su SHA-256
   contra el publicado en la Release. Nunca incluir configuración local en el JAR.
3. Programar mantenimiento y respaldar PostgreSQL con pg_dump, archivos subidos
   y configuración. Probar restauración. Para consistencia, detener escrituras.
4. Ejecutar: sudo ./update.sh vX.Y.Z /ruta/candidato.jar
   Si el puerto/IP difiere: sudo APP_CHECK_URL=http://IP:PUERTO/login.html ./update.sh ...
5. El script descarga el tag, valida estructura básica del JAR, guarda configuración,
   JAR previo y commit en .releases, detiene solo Java de esta instalación,
   cambia a tag detached e inicia candidato. No ejecuta deploy.sh ni Gradle.
6. Verifica HTTP 200 de login; esto NO prueba DB, SMTP ni Elasticsearch.
   Hacer las pruebas funcionales antes de abrir el servicio a usuarios.

El script exige confirmar procedencia del JAR y respaldo: no realiza pg_dump ni
verifica automáticamente la correspondencia binaria entre JAR y commit.
Hay una interrupción de servicio; no se promete actualización sin downtime.
Si falla, revisar log. Para volver: detener candidato, recuperar JAR previo,
configuración y commit anotados en .releases. Restaurar DB/archivos solo mediante
procedimiento aprobado y si hubo cambios incompatibles. Nunca revertir esquema
automáticamente ni borrar datos para intentar recuperar el arranque.
No hay tags/releases nuevos creados automáticamente por estos scripts.
