# Auditoría local de secretos

Desde la raíz del repositorio:

```sh
python3 -m unittest discover -s tools/security -p 'test_*.py'
python3 tools/security/audit_secrets.py
python3 tools/security/audit_secrets.py --history
```

Salida JSON: ruta, línea, regla y (en historial) objeto Git. **Nunca imprime valores ni huellas individuales de contraseñas.** Código 1 indica hallazgos; 0 indica ausencia de patrones reconocidos, no garantía de ausencia de secretos. La auditoría no modifica Git, configuración, servicios ni cuentas. `deploy.sh` reconoce placeholders completos de entorno al reutilizar propiedades, sin ejecutar su contenido. Revisar resultados en privado.

Detecta encabezados de claves privadas, tokens GitHub y claves de acceso AWS, además de credenciales literales en `.properties`/`.env` versionados fuera de fixtures `src/test`. Los patrones de claves/tokens se revisan incluso en tests. No sustituye una herramienta de detección especializada, revisión humana, análisis de imágenes/JAR publicados o rotación de credenciales expuestas.

## Plantillas y compatibilidad

Las plantillas de configuración ya no contienen un JWT compartido ni contraseñas predeterminadas de base de datos/RabbitMQ. Utilizar configuración externa generada por `deploy.sh` o variables de entorno:

- `BASE_REPO_DB_PASSWORD`: contraseña real de la base de datos configurada.
- `BASE_REPO_RABBIT_PASSWORD`: solo si se habilita mensajería.
- `BASE_REPO_JWT_SECRET`: Docker requiere una clave única de al menos 32 bytes; no usar ejemplos compartidos.

El despliegue existente mantiene sus propiedades externas. **Si se usaba directamente la plantilla con un H2 existente, conservar su contraseña anterior en configuración externa/env antes de iniciar; cambiar una plantilla no cambia la contraseña de la base de datos.** No elimina ni migra datos.

## Hallazgos históricos

Eliminar un valor del árbol actual no lo elimina de commits anteriores. La rotación de cuentas institucionales y una eventual reescritura de historia requieren autorización separada y coordinación de clones/releases. Este script no realiza ninguna de ellas. El gate `--history` puede seguir fallando legítimamente mientras esos asuntos estén pendientes.
