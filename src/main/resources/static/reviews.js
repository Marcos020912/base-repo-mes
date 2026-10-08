const user = auth.user();
if (!['CURATOR', 'ADMINISTRATOR'].includes(user?.role)) location.replace('index.html');
document.querySelector('#current-user').textContent = user?.username || '';
if (user?.role === 'ADMINISTRATOR') document.querySelector('#users-nav').hidden = false;
document.querySelectorAll('[data-logout]').forEach(button => button.addEventListener('click', auth.logout));
const list = document.querySelector('#reviews-list');
const preservation = document.querySelector('#preservation-summary');
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
      const verify = action('Comprobar SHA-256', 'secondary', async () => {
        try {
          const result = await request(`/api/v1/scientific/${encodeURIComponent(id)}/fixity?path=${encodeURIComponent(file.path)}`, { method: 'POST' });
          toast[result.status === 'MATCH' ? 'success' : 'error'](`Integridad de ${file.path}: ${result.status}.`);
        } catch (error) { toast.error(error.message); }
      });
      row.append(label, download, verify); files.append(row);
    }
    if (!listing.files.length) { const empty = document.createElement('li'); empty.textContent = 'No hay archivos en esta página.'; files.append(empty); }
    const pagination = document.createElement('div'); pagination.className = 'pagination';
    if (listing.pages > 1) {
      const previous = action('← Anterior', 'secondary', () => preview(id, container, page - 1)); previous.disabled = page === 0;
      const next = action('Siguiente →', 'secondary', () => preview(id, container, page + 1)); next.disabled = page + 1 >= listing.pages;
      const label = document.createElement('span'); label.textContent = `Página ${page + 1} de ${listing.pages}`;
      pagination.append(previous, label, next);
    }
    const packageButton = action('Descargar paquete de preservación', 'secondary', async () => {
      try {
        const response = await fetch(`/api/v1/scientific/preservation/${encodeURIComponent(id)}/package`, { headers: auth.headers() });
        if (!response.ok) throw new Error('No se pudo preparar el paquete; comprueba que todos los archivos estén presentes.');
        const url = URL.createObjectURL(await response.blob());
        const link = document.createElement('a'); link.href = url; link.download = `preservation-${id}.zip`; link.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      } catch (error) { toast.error(error.message); }
    });
    const historyButton = action('Ver procedencia de archivos', 'secondary', async () => {
      try {
        const history = await request(`/api/v1/scientific/${encodeURIComponent(id)}/file-history?size=20`);
        const lines = (history.content || []).map(event => `${new Date(event.occurredAt).toLocaleString()} · ${event.actor} · ${event.action} · ${event.relativePath}`);
        historyView.textContent = lines.length ? lines.join('\n') : 'No hay eventos de carga o borrado registrados para este depósito.';
        historyView.hidden = false;
      } catch (error) { toast.error(error.message); }
    });
    const historyView = document.createElement('pre'); historyView.className = 'review-description'; historyView.hidden = true;
    const shareHeading = document.createElement('h4'); shareHeading.textContent = 'Revisión externa';
    const shareNote = document.createElement('p'); shareNote.textContent = 'Los enlaces son de solo lectura y caducan en 48 horas. Compártelos únicamente con el revisor previsto; el enlace completo se muestra una sola vez.';
    const shareActions = document.createElement('div'); shareActions.className = 'button-row';
    const shareResult = document.createElement('p'); shareResult.className = 'review-share-result';
    const linksView = document.createElement('div'); linksView.className = 'review-links';
    const showLinks = async () => {
      const links = await request(`/api/v1/scientific/${encodeURIComponent(id)}/review-links`);
      linksView.replaceChildren();
      if (!links.length) linksView.append(document.createTextNode('No hay enlaces registrados.'));
      for (const link of links) {
        const line = document.createElement('p');
        const state = link.revokedAt ? 'Revocado' : new Date(link.expiresAt).getTime() <= Date.now() ? 'Vencido' : 'Activo';
        line.append(document.createTextNode(`Enlace ${link.id} · ${state} · vence ${new Date(link.expiresAt).toLocaleString('es')}`));
        if (state === 'Activo') line.append(action('Revocar', 'danger-outline', async () => {
          try { await request(`/api/v1/scientific/${encodeURIComponent(id)}/review-links/${link.id}`, {method:'DELETE'});
            toast.success('Enlace revocado.'); await showLinks(); } catch (error) { toast.error(error.message); }
        }));
        linksView.append(line);
      }
    };
    shareActions.append(action('Crear enlace 48 h', 'secondary', async () => {
      try {
        const created = await request(`/api/v1/scientific/${encodeURIComponent(id)}/review-links`, {
          method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({hours:48})
        });
        const url = new URL(created.relativeUrl, location.origin).href;
        shareResult.replaceChildren(document.createTextNode('Enlace creado. Cópialo ahora; no podrá volver a mostrarse: '));
        const input = document.createElement('input'); input.readOnly = true; input.value = url; input.setAttribute('aria-label','Enlace privado de revisión');
        const copy = action('Copiar', 'secondary', async () => { try { await navigator.clipboard.writeText(url); toast.success('Enlace copiado.'); } catch { input.select(); toast.error('Copia manualmente el enlace seleccionado.'); } });
        shareResult.append(input, copy); await showLinks();
      } catch (error) { toast.error(error.message); }
    }), action('Ver enlaces', 'secondary', async () => { try { await showLinks(); } catch (error) { toast.error(error.message); } }));
    container.append(heading, byline, validation, descriptionHeading, description, filesHeading, files, pagination,
      packageButton, historyButton, historyView, shareHeading, shareNote, shareActions, shareResult, linksView);
  } catch (error) { container.textContent = ''; toast.error(error.message); }
}
async function load() {
  list.textContent = 'Cargando…';
  try {
    const doiEnabled = (await request('/api/v1/scientific/doi/config')).enabled;
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
        if (!doiEnabled && !record.versionDoi) { toast.error('Registra primero el DOI de esta versión.'); return; }
        if (!confirm(doiEnabled?'¿Confirmas la revisión? Se reservarán y publicarán en DataCite el DOI de versión y el conceptual.':'Confirma que el DOI mostrado ya está registrado externamente y que revisaste metadatos y archivos. ¿Publicar esta versión inmutable?')) return;
        try { if(doiEnabled)await request(`/api/v1/scientific/${encodeURIComponent(record.resourceId)}/doi/publish`, { method: 'POST' });else await request(`/api/v1/scientific/${encodeURIComponent(record.resourceId)}/publish`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ doiRegisteredExternally: true }) }); toast.success('Versión publicada.'); await load(); }
        catch (error) { toast.error(error.message); }
      }));
      card.append(heading, info, controls, detail); list.append(card);
    }
  } catch (error) { list.textContent = ''; toast.error(error.message); }
}
async function loadPreservation() {
  try {
    const metrics = await request('/api/v1/scientific/preservation/metrics');
    const latest = metrics.latestAudit?.id ? `${metrics.latestAudit.status}: ${metrics.latestAudit.checked} comprobados, ${metrics.latestAudit.mismatched} alterados, ${metrics.latestAudit.missing} ausentes, ${metrics.latestAudit.noBaseline} sin huella inicial.` : 'Todavía no hay auditorías.';
    preservation.textContent = `${metrics.published} publicados · ${metrics.inReview} en revisión · ${metrics.files} archivos · ${metrics.filesChecked} con resultado de verificación · ${metrics.mismatched} alterados · ${metrics.missing} ausentes. Última auditoría: ${latest}`;
  } catch (error) { preservation.textContent = 'No se pudieron consultar las métricas de preservación.'; }
}
document.querySelector('#start-audit').addEventListener('click', async () => {
  if (!confirm('Esta operación leerá todos los archivos almacenados. ¿Iniciar auditoría?')) return;
  try {
    await request('/api/v1/scientific/preservation/audits', { method: 'POST' });
    toast.success('Auditoría iniciada. Consulta el estado más tarde.');
    await loadPreservation();
  } catch (error) { toast.error(error.message); }
});
document.querySelector('#refresh-reviews').addEventListener('click', load);
load();
loadPreservation();
