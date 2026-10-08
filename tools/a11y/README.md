# Auditoría automatizada local de accesibilidad

```bash
cd tools/a11y
npm ci
CHROME_BIN=/usr/bin/google-chrome npm run audit
```

Sirve `src/main/resources/static` por HTTP local, bloquea los scripts de la app
para no llamar a APIs ni necesitar cuentas, y ejecuta las reglas automáticas
WCAG A/AA de axe-core en las trece páginas, diálogos y pasos del asistente.
Repite las trece páginas a 320 px y rechaza el desbordamiento horizontal.
También comprueba que el primer Tab alcanza el enlace de salto y que Enter
transfiere el foco a `<main>`. Devuelve código 1 ante una infracción. No envía
datos a servicios externos.

**Alcance limitado:** no cubre datos reales, formularios después de interacción,
las vistas cargadas desde el backend, navegación real por teclado, lector de
pantalla, zoom/reflujo ni todas las condiciones WCAG 2.2 AA. Se requiere
revisión manual y funcional antes de afirmar conformidad.
