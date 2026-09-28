const id = new URLSearchParams(location.search).get('id');
const status = document.querySelector('#status');
let detail;
function node(tag, value, className) { const item = document.createElement(tag); item.textContent = value; if (className) item.className = className; return item; }
function field(label, value) { if (!value) return; const list = document.querySelector('#identity'); list.append(node('dt', label), node('dd', value)); }
function renderMarkdown(source) {
  const root = document.querySelector('#description'); root.replaceChildren();
  for (const line of source.split(/\r?\n/)) {
    if (!line.trim()) continue;
    const image = line.match(/^!\[([^\]]*)\]\(([^)]+)\)$/);
    if (image && !/^(?:[a-z]+:|\/|\.\.)/i.test(image[2])) {
      const img = document.createElement('img'); img.alt = image[1]; img.loading = 'lazy';
      img.src = `/api/v1/public/resources/${encodeURIComponent(id)}/file?path=${encodeURIComponent(image[2])}&inline=true`;
      root.append(img); continue;
    }
    const match = line.match(/^(#{1,3})\s+(.+)$/);
    if (match) root.append(node(`h${match[1].length}`, match[2]));
    else if (/^[-*]\s+/.test(line)) root.append(node('p', `• ${line.slice(2)}`));
    else root.append(node('p', line));
  }
}
async function loadFiles(page = 0) {
  const response = await fetch(`/api/v1/public/resources/${encodeURIComponent(id)}/files?page=${page}&size=50`);
  if (!response.ok) throw new Error('No se pudieron consultar los archivos.');
  const data = await response.json(); const list = document.querySelector('#files'); list.replaceChildren();
  if (!data.files.length) list.append(node('p', 'No hay archivos disponibles.', 'empty'));
  for (const file of data.files) {
    const row = document.createElement('div'); row.className = 'file-row';
    const info = document.createElement('span'); info.append(node('strong', file.path), node('small', `${file.size} bytes · ${file.mediaType || 'archivo'}`));
    if (file.sha256) { const checksum = node('small', `SHA-256: ${file.sha256}`); checksum.title = 'Huella registrada al subir el archivo; puede verificarla tras descargarlo con sha256sum.'; info.append(checksum); }
    const link = document.createElement('a'); link.className = 'secondary'; link.textContent = 'Descargar'; link.href = `/api/v1/public/resources/${encodeURIComponent(id)}/file?path=${encodeURIComponent(file.path)}`;
    const canDownload = detail.accessLevel === 'OPEN' ||
      (detail.accessLevel === 'EMBARGOED' && detail.embargoUntil && Date.now() >= new Date(detail.embargoUntil).getTime());
    row.append(info, canDownload ? link : node('span', 'Acceso restringido', 'muted')); list.append(row);
  }
  const pager = document.querySelector('#files-pagination'); pager.replaceChildren();
  if (data.pages > 1) {
    const prev = node('button', '← Anterior', 'secondary'); prev.disabled = page === 0; prev.onclick = () => loadFiles(page - 1);
    const next = node('button', 'Siguiente →', 'secondary'); next.disabled = page + 1 >= data.pages; next.onclick = () => loadFiles(page + 1);
    pager.append(prev, node('span', `Página ${page + 1} de ${data.pages}`), next);
  }
}
async function load() {
  if (!id) { status.textContent = 'Falta el identificador del recurso.'; return; }
  try {
    const response = await fetch(`/api/v1/public/resources/${encodeURIComponent(id)}`);
    const data = await response.json();
    if (response.status === 410) { document.querySelector('#title').textContent = data.title || 'Recurso retirado'; status.textContent = `Este recurso fue retirado. Motivo: ${data.reason || 'No informado'}`; return; }
    if (!response.ok) throw new Error('El recurso no está publicado o no está disponible.');
    detail = data; document.querySelector('#landing').hidden = false;
    document.querySelector('#title').textContent = data.title;
    document.querySelector('#subtitle').textContent = `${data.authors?.join(', ') || 'Autoría no informada'} · ${data.year || 's. f.'}`;
    document.querySelector('#version').textContent = `Versión ${data.version || 'no informada'}`;
    field('DOI', data.doi); field('DOI conceptual', data.conceptualDoi); field('Licencia', data.license);
    field('Acceso', ({OPEN:'Abierto',RESTRICTED:'Restringido',EMBARGOED:'Embargo'})[data.accessLevel] || data.accessLevel);
    field('Fin del embargo', data.embargoUntil ? new Date(data.embargoUntil).toLocaleString('es') : null);
    field('Institución', data.institution); field('ORCID', data.orcid); field('ROR', data.ror);
    field('Idioma', data.language); field('Disciplina', data.discipline); field('Palabras clave', data.keywords);
    field('Métodos', data.methodology); field('Publicaciones relacionadas', data.relatedPublications);
    field('Publicado', data.publishedAt ? new Date(data.publishedAt).toLocaleDateString('es') : null);
    if (data.previousResourceId) {
      const previous = document.createElement('a'); previous.href = `public-resource.html?id=${encodeURIComponent(data.previousResourceId)}`;
      previous.textContent = 'Ver versión anterior'; document.querySelector('#version-links').append(previous);
    }
    if (data.newerVersionId) {
      const warning = document.createElement('p'); warning.className = 'version-warning';
      warning.textContent = 'Está consultando una versión anterior. ';
      const newer = document.createElement('a'); newer.href = `public-resource.html?id=${encodeURIComponent(data.newerVersionId)}`;
      newer.textContent = 'Ver la versión posterior'; warning.append(newer);
      document.querySelector('#version-links').append(warning);
    }
    renderMarkdown(data.markdown || ''); await loadFiles(); status.textContent = '';
    const citation = `${data.authors?.join(', ') || 'Autor no informado'} (${data.year || 's. f.'}). ${data.title} (versión ${data.version}) [${data.type || 'Recurso'}]. ${data.publisher || 'Editorial no informada'}. https://doi.org/${data.doi}`;
    document.querySelector('#citation').textContent = citation;
    document.querySelector('#copy-citation').onclick = async () => { try { await navigator.clipboard.writeText(citation); status.textContent = 'Cita copiada.'; } catch { status.textContent = 'No se pudo copiar la cita.'; } };
  } catch (error) { status.textContent = error.message; }
}
document.querySelector('#export').addEventListener('click', () => {
  if (!detail) return;
  const format = document.querySelector('#format').value;
  location.href = `/api/v1/scientific/${encodeURIComponent(id)}/citation?format=${encodeURIComponent(format)}`;
});
load();
