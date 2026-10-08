const id = new URLSearchParams(location.search).get('id') || (location.pathname.startsWith('/datasets/') ? decodeURIComponent(location.pathname.slice('/datasets/'.length)) : null);
const status = document.querySelector('#status');
let detail;
function canDownloadContent() {
  return detail && (detail.accessLevel === 'OPEN' ||
    (detail.accessLevel === 'EMBARGOED' && detail.embargoUntil && Date.now() >= new Date(detail.embargoUntil).getTime()));
}
function node(tag, value, className) { const item = document.createElement(tag); item.textContent = value; if (className) item.className = className; return item; }
function field(label, value) { if (!value) return; const list = document.querySelector('#identity'); list.append(node('dt', label), node('dd', value)); }
function doiField(label, value) {
  if (!value) return;
  const list = document.querySelector('#identity'); const description = node('dt', label); const detail = document.createElement('dd');
  if (/^10\.\d{4,9}\/\S+$/.test(value)) {
    const link = document.createElement('a'); link.href = `https://doi.org/${value.split('/').map(encodeURIComponent).join('/')}`; link.textContent = value;
    link.rel = 'noopener noreferrer'; detail.append(link);
  } else detail.textContent = value;
  list.append(description, detail);
}
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
function renderRelations(relations) {
  if (!relations?.length) return;
  const section = document.createElement('section'); section.className = 'panel';
  section.append(node('h2', 'Recursos relacionados'));
  for (const relation of relations) {
    const row = document.createElement('div'); row.className = 'related-resource';
    const label = node('p', `${relation.kind} · ${relation.relationType}`);
    const link = document.createElement('a');
    link.href = relation.identifierType === 'DOI' ? `https://doi.org/${relation.identifier.split('/').map(encodeURIComponent).join('/')}` : relation.identifier;
    link.rel = 'noopener noreferrer'; link.textContent = relation.title || relation.identifier;
    row.append(label, link); section.append(row);
  }
  document.querySelector('#landing').append(section);
}
function renderAuthors(authors) {
  if (!authors?.length) return;
  const section = document.createElement('section'); section.className = 'panel';
  section.append(node('h2', 'Autores e instituciones'));
  for (const author of authors) {
    const row = document.createElement('p'); row.className = 'author-identity';
    row.append(node('strong', [author.givenName, author.familyName].filter(Boolean).join(' ') || 'Autor sin nombre'));
    if (author.orcid) {
      const link = document.createElement('a'); link.href = `https://orcid.org/${author.orcid.replace(/^https:\/\/orcid\.org\//, '')}`;
      link.textContent = `ORCID ${author.orcid}`; link.rel = 'noopener noreferrer'; row.append(' · ', link);
      const status = document.createElement('small');
      status.textContent = author.orcidAuthenticated ? ' · ORCID autenticado por el depositante (autoría no contrastada)' : ' · ORCID declarado, sin autenticar';
      row.append(status);
    }
    const affiliations = Array.isArray(author.affiliations) ? author.affiliations :
      (author.institution ? [{institution: author.institution, ror: author.ror}] : []);
    for (const affiliation of affiliations) {
      row.append(node('span', ` · ${affiliation.institution}`));
      if (affiliation.ror) {
        const link = document.createElement('a'); link.href = `https://ror.org/${affiliation.ror.replace(/^https:\/\/ror\.org\//, '')}`;
        link.textContent = `ROR ${affiliation.ror}`; link.rel = 'noopener noreferrer'; row.append(' · ', link);
      }
    }
    section.append(row);
  }
  document.querySelector('#description').closest('.panel').before(section);
}
function renderFunding(funding) {
  if (!funding?.length) return;
  const section = document.createElement('section'); section.className = 'panel';
  section.append(node('h2', 'Financiación y proyectos'));
  for (const item of funding) {
    const row = document.createElement('p'); row.className = 'related-resource';
    row.append(node('strong', item.funderName));
    if (item.funderRor) {
      const link = document.createElement('a'); link.href = `https://ror.org/${item.funderRor.replace(/^https:\/\/ror\.org\//, '')}`;
      link.textContent = 'ROR'; link.rel = 'noopener noreferrer'; row.append(' · ', link);
    }
    if (item.awardTitle) row.append(` · Proyecto: ${item.awardTitle}`);
    if (item.awardNumber) row.append(` · Nº ${item.awardNumber}`);
    section.append(row);
  }
  document.querySelector('#description').closest('.panel').before(section);
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
    const fixityLabels = {MATCH:'Integridad comprobada',MISMATCH:'Advertencia: el archivo no coincide con su huella',MISSING_FILE:'Advertencia: archivo no disponible',NO_BASELINE:'Sin huella inicial',UNSUPPORTED_URI:'Comprobación no admitida',READ_ERROR:'No se pudo comprobar'};
    if (file.fixityStatus) {
      const verified = node('small', `${fixityLabels[file.fixityStatus] || file.fixityStatus}${file.fixityCheckedAt ? ` · ${new Date(file.fixityCheckedAt).toLocaleString('es')}` : ''}`);
      verified.className = file.fixityStatus === 'MATCH' ? 'quality-ok' : 'quality-missing'; info.append(verified);
    } else info.append(node('small', 'Integridad aún no comprobada'));
    const link = document.createElement('a'); link.className = 'secondary'; link.textContent = 'Descargar'; link.href = `/api/v1/public/resources/${encodeURIComponent(id)}/file?path=${encodeURIComponent(file.path)}`;
    link.addEventListener('click', event => { event.preventDefault(); downloadPublic(link.href, file.path); });
    row.append(info, canDownloadContent() ? link : node('span', 'Acceso restringido', 'muted')); list.append(row);
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
    const archive = document.querySelector('#download-archive');
    archive.hidden = !canDownloadContent();
    archive.onclick = event => { event.preventDefault(); downloadPublic(archive.href, id + '.zip'); };
    if (!archive.hidden) archive.href = `/api/v1/public/resources/${encodeURIComponent(id)}/archive`;
    document.querySelector('#title').textContent = data.title;
    document.querySelector('#subtitle').textContent = `${data.authors?.join(', ') || 'Autoría no informada'} · ${data.year || 's. f.'}`;
    document.querySelector('#version').textContent = `Versión ${data.version || 'no informada'}`;
    doiField('DOI', data.doi); doiField('DOI conceptual', data.conceptualDoi); field('Licencia', data.license);
    field('Acceso', ({OPEN:'Abierto',RESTRICTED:'Restringido',EMBARGOED:'Embargo'})[data.accessLevel] || data.accessLevel);
    field('Fin del embargo', data.embargoUntil ? new Date(data.embargoUntil).toLocaleString('es') : null);
    field('Institución', data.institution);
    if (!data.authorIdentities?.some(item => item.orcid)) field('ORCID', data.orcid);
    if (!data.authorIdentities?.some(item => item.ror)) field('ROR', data.ror);
    field('Idioma', data.language); field('Disciplina', data.discipline); field('Palabras clave', data.keywords);
    field('Métodos', data.methodology); field('Publicaciones relacionadas', data.relatedPublications);
    field('Publicado', data.publishedAt ? new Date(data.publishedAt).toLocaleDateString('es') : null);
    if (data.previousResourceId) {
      const previous = document.createElement('a'); previous.href = `/datasets/${encodeURIComponent(data.previousResourceId)}`;
      previous.textContent = 'Ver versión anterior'; document.querySelector('#version-links').append(previous);
    }
    if (data.newerVersionId) {
      const warning = document.createElement('p'); warning.className = 'version-warning';
      warning.textContent = 'Está consultando una versión anterior. ';
      const newer = document.createElement('a'); newer.href = `/datasets/${encodeURIComponent(data.newerVersionId)}`;
      newer.textContent = 'Ver la versión posterior'; warning.append(newer);
      document.querySelector('#version-links').append(warning);
    }
    renderAuthors(data.authorIdentities); renderFunding(data.funding); renderMarkdown(data.markdown || ''); renderRelations(data.relations); await loadFiles(); status.textContent = '';
    const citation = `${data.authors?.join(', ') || 'Autor no informado'} (${data.year || 's. f.'}). ${data.title} (versión ${data.version}) [${data.type || 'Recurso'}]. ${data.publisher || 'Editorial no informada'}. https://doi.org/${data.doi}`;
    document.querySelector('#citation').textContent = citation;
    document.querySelector('#copy-citation').onclick = async () => { try { await navigator.clipboard.writeText(citation); status.textContent = 'Cita copiada.'; } catch { status.textContent = 'No se pudo copiar la cita.'; } };
  } catch (error) { status.textContent = error.message; }
}
document.querySelector('#export').addEventListener('click', () => {
  if (!detail) return;
  const format = document.querySelector('#format').value;
  const extension = ({bibtex:'bib', ris:'ris', 'csl-json':'json'})[format] || 'txt';
  downloadPublic(`/api/v1/scientific/${encodeURIComponent(id)}/citation?format=${encodeURIComponent(format)}`, `citation.${extension}`);
});
async function downloadPublic(url, filename) {
  try { await transfers.download(url, filename); status.textContent = 'Descarga preparada.'; }
  catch (error) { status.textContent = error.name === 'AbortError' ? 'Descarga cancelada.' : error.message; }
}
load();
