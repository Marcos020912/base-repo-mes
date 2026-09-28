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
      const link = document.createElement('a'); link.href = `resource.html?id=${encodeURIComponent(record.resourceId)}`; link.className = 'secondary'; link.textContent = 'Abrir ficha';
      controls.append(link, action('Devolver a borrador', 'secondary', async () => {
        if (!confirm('¿Devolver este depósito a su autor?')) return;
        try { await request(`/api/v1/scientific/${encodeURIComponent(record.resourceId)}/return-to-draft`, { method: 'POST' }); toast.success('Depósito devuelto a borrador.'); await load(); }
        catch (error) { toast.error(error.message); }
      }), action('Publicar', 'primary', async () => {
        if (!record.versionDoi) { toast.error('Registra primero el DOI de esta versión.'); return; }
        if (!confirm('Confirma que el DOI mostrado ya está registrado externamente y que revisaste metadatos y archivos. ¿Publicar esta versión inmutable?')) return;
        try { await request(`/api/v1/scientific/${encodeURIComponent(record.resourceId)}/publish`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ doiRegisteredExternally: true }) }); toast.success('Versión publicada.'); await load(); }
        catch (error) { toast.error(error.message); }
      }));
      card.append(heading, info, controls); list.append(card);
    }
  } catch (error) { list.textContent = ''; toast.error(error.message); }
}
document.querySelector('#refresh-reviews').addEventListener('click', load);
load();
