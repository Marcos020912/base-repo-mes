const user = auth.user();
if (!['CURATOR', 'ADMINISTRATOR'].includes(user?.role)) location.replace('index.html');
document.querySelector('#current-user').textContent = user?.username || '';
if (user?.role === 'ADMINISTRATOR') document.querySelector('#users-nav').hidden = false;
document.querySelectorAll('[data-logout]').forEach(button => button.addEventListener('click', auth.logout));
const list = document.querySelector('#reviews-list');
async function request(path, options = {}) {
  const response = await fetch(path, { ...options, headers: auth.headers({ Accept: 'application/json', ...options.headers }) });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.detail || data.message || 'No se pudo completar la operación.');
  return data;
}
function action(label, className, handler) {
  const button = document.createElement('button');
  button.type = 'button'; button.className = className; button.textContent = label;
  button.addEventListener('click', handler); return button;
}
async function preview(id, container, page = 0) {
  container.textContent = 'Cargando vista previa…';
  try {
    const [metadata, listing, quality] = await Promise.all([
      request(`/api/v1/scientific/review/${encodeURIComponent(id)}`),
      request(`/api/v1/scientific/review/${encodeURIComponent(id)}/files?page=${page}`),
      request(`/api/v1/scientific/${encodeURIComponent(id)}/quality`)
    ]);
    container.replaceChildren();
    const heading = document.createElement('h3'); heading.textContent = metadata.title;
    const byline = document.createElement('p'); byline.textContent = `${metadata.authors.join(', ') || 'Autoría no informada'} · ${metadata.publisher || 'Institución no informada'} · ${metadata.year || 'Año no informado'}`;
    const validation = document.createElement('p'); validation.textContent = `Calidad: ${quality.completionPercent} %. ${quality.blockers.length ? `Faltan: ${quality.blockers.join(', ')}` : 'Requisitos automáticos completos.'}`;
    const descriptionHeading = document.createElement('h4'); descriptionHeading.textContent = 'description.md (vista segura de texto)';
    const description = document.createElement('pre'); description.className = 'review-description'; description.textContent = metadata.markdown;
    const filesHeading = document.createElement('h4'); filesHeading.textContent = 'Archivos para revisar';
    const files = document.createElement('ul'); files.className = 'review-files';
    for (const file of listing.files) {
      const row = document.createElement('li');
      const label = document.createElement('span'); label.textContent = `${file.path} · ${file.size} bytes${file.sha256 ? ` · SHA-256 ${file.sha256}` : ''}`;
      const download = action('Descargar', 'secondary', async () => {
        try {
          const response = await fetch(`/api/v1/scientific/review/${encodeURIComponent(id)}/file?path=${encodeURIComponent(file.path)}`, { headers: auth.headers() });
          if (!response.ok) throw new Error('No se pudo descargar el archivo de revisión.');
          const url = URL.createObjectURL(await response.blob()); const anchor = document.createElement('a');
          anchor.href = url; anchor.download = file.path.split('/').pop(); anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (error) { toast.error(error.message); }
      });
      row.append(label, download); files.append(row);
    }
    if (!listing.files.length) { const empty = document.createElement('li'); empty.textContent = 'No hay archivos en esta página.'; files.append(empty); }
    const pagination = document.createElement('div'); pagination.className = 'pagination';
    if (listing.pages > 1) {
      const previous = action('← Anterior', 'secondary', () => preview(id, container, page - 1)); previous.disabled = page === 0;
      const next = action('Siguiente →', 'secondary', () => preview(id, container, page + 1)); next.disabled = page + 1 >= listing.pages;
      const label = document.createElement('span'); label.textContent = `Página ${page + 1} de ${listing.pages}`;
      pagination.append(previous, label, next);
    }
    container.append(heading, byline, validation, descriptionHeading, description, filesHeading, files, pagination);
  } catch (error) { container.textContent = ''; toast.error(error.message); }
}
async function load() {
  list.textContent = 'Cargando…';
  try {
    const records = await request('/api/v1/scientific/reviews');
    list.replaceChildren();
    if (!records.length) { const empty = document.createElement('p'); empty.className = 'panel empty'; empty.textContent = 'No hay depósitos pendientes de revisión.'; list.append(empty); return; }
    for (const record of records) {
      const card = document.createElement('article'); card.className = 'panel review-card';
      const heading = document.createElement('h2'); heading.textContent = record.resourceId;
      const info = document.createElement('p'); info.textContent = `Versión: ${record.versionLabel || '—'} · DOI: ${record.versionDoi || '—'} · Licencia: ${record.licenseId || '—'}`;
      const controls = document.createElement('div'); controls.className = 'button-row';
      const detail = document.createElement('section'); detail.className = 'review-preview'; detail.hidden = true;
      controls.append(action('Revisar metadatos y archivos', 'secondary', async () => {
        detail.hidden = !detail.hidden;
        if (!detail.hidden) await preview(record.resourceId, detail);
      }), action('Devolver a borrador', 'secondary', async () => {
        if (!confirm('¿Devolver este depósito a su autor?')) return;
        try { await request(`/api/v1/scientific/${encodeURIComponent(record.resourceId)}/return-to-draft`, { method: 'POST' }); toast.success('Depósito devuelto a borrador.'); await load(); }
        catch (error) { toast.error(error.message); }
      }), action('Publicar', 'primary', async () => {
        if (!record.versionDoi) { toast.error('Registra primero el DOI de esta versión.'); return; }
        if (!confirm('Confirma que el DOI mostrado ya está registrado externamente y que revisaste metadatos y archivos. ¿Publicar esta versión inmutable?')) return;
        try { await request(`/api/v1/scientific/${encodeURIComponent(record.resourceId)}/publish`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ doiRegisteredExternally: true }) }); toast.success('Versión publicada.'); await load(); }
        catch (error) { toast.error(error.message); }
      }));
      card.append(heading, info, controls, detail); list.append(card);
    }
  } catch (error) { list.textContent = ''; toast.error(error.message); }
}
document.querySelector('#refresh-reviews').addEventListener('click', load);
load();
