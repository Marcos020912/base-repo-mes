const form = document.querySelector('#wizard-form');
const $ = selector => document.querySelector(selector);
auth.requireLogin();
const user = auth.user();
$('#current-user').textContent = user?.username || '';
if (user?.role === 'ADMINISTRATOR') $('#users-nav').hidden = false;
document.querySelectorAll('[data-logout]').forEach(button => button.addEventListener('click', auth.logout));
const params = new URLSearchParams(location.search);
const basedOnId = params.get('basedOn');
let step = 0;
let busy = false;
let createdId = null;
let basedOnVerified = !basedOnId;
let previewGeneration = 0;
const policies = {
  IMAGE: { accept: '.jpg,.jpeg,.png,.gif,.webp,.svg,.tif,.tiff,.bmp', extensions: ['jpg','jpeg','png','gif','webp','svg','tif','tiff','bmp'], hint: 'Imágenes JPG, PNG, GIF, WebP, SVG, TIFF o BMP' },
  TEXT: { accept: '.pdf,.doc,.docx,.odt,.rtf,.txt,.md,.epub', extensions: ['pdf','doc','docx','odt','rtf','txt','md','epub'], hint: 'PDF, DOC, DOCX, ODT, RTF, TXT, MD o EPUB' },
  AUDIOVISUAL: { accept: '.mp4,.webm,.mov,.avi,.mkv,.mpeg,.mpg,.m4v', extensions: ['mp4','webm','mov','avi','mkv','mpeg','mpg','m4v'], hint: 'MP4, WebM, MOV, AVI, MKV, MPEG o M4V' },
  DATASET: { accept: '.csv,.tsv,.tab,.xls,.xlsx,.ods,.parquet,.sav,.dta,.json,.xml', extensions: ['csv','tsv','tab','xls','xlsx','ods','parquet','sav','dta','json','xml'], hint: 'CSV, XLS, XLSX, TSV, ODS, Parquet, SAV, DTA, JSON o XML' },
  OTHER: { accept: '', extensions: null, hint: 'Cualquier formato' }
};
const field = name => form.elements.namedItem(name);
const value = name => field(name).value.trim();
const files = () => Array.from($('#dataset-files').files);
const errorBox = $('#wizard-message');

function report(message, kind = '') {
  errorBox.className = `message ${kind}`;
  errorBox.replaceChildren(document.createTextNode(message));
}
function addCreator(givenName = '', familyName = '') {
  const row = document.createElement('div'); row.className = 'creator-row';
  const given = document.createElement('label'); given.textContent = 'Nombre';
  const givenInput = document.createElement('input'); givenInput.className = 'creator-given'; givenInput.required = true; givenInput.maxLength = 255; givenInput.value = givenName; given.append(givenInput);
  const family = document.createElement('label'); family.textContent = 'Apellido';
  const familyInput = document.createElement('input'); familyInput.className = 'creator-family'; familyInput.maxLength = 255; familyInput.value = familyName; family.append(familyInput);
  const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'secondary'; remove.textContent = 'Quitar';
  remove.addEventListener('click', () => { if ($('#creator-list').childElementCount > 1) row.remove(); else report('Debe quedar al menos un autor.', 'error'); });
  row.append(given, family, remove); $('#creator-list').append(row);
}
function creators() {
  return Array.from(document.querySelectorAll('.creator-row')).map(row => ({
    givenName: row.querySelector('.creator-given').value.trim(),
    familyName: row.querySelector('.creator-family').value.trim()
  }));
}
function applyPolicy() {
  const policy = policies[value('type')] || policies.OTHER;
  $('#dataset-files').accept = policy.accept ? `${policy.accept},.zip` : '';
  $('#file-type-hint').textContent = `${policy.hint}. También se permite un ZIP validado por el servidor.`;
  updateFileCount();
}
function updateFileCount() {
  const selected = files();
  $('#file-count').textContent = selected.length ? `${selected.length} archivo(s), ${selected.reduce((total, file) => total + file.size, 0).toLocaleString('es')} bytes en total.` : 'Aún no hay archivos seleccionados.';
}
function showStep(next) {
  step = next;
  const generation = ++previewGeneration;
  document.querySelectorAll('.wizard-panel').forEach(panel => { panel.hidden = Number(panel.dataset.step) !== step; });
  document.querySelectorAll('#wizard-steps li').forEach((item, index) => {
    item.classList.toggle('active', index === step);
    if (index === step) item.setAttribute('aria-current', 'step'); else item.removeAttribute('aria-current');
  });
  $('#wizard-back').hidden = step === 0;
  $('#wizard-next').hidden = step === 3;
  $('#save-draft').hidden = step !== 3;
  $('#save-submit').hidden = step !== 3;
  report('');
  if (step === 3) {
    $('#save-draft').disabled = true; $('#save-submit').disabled = true;
    renderPreview().then(() => { if (generation === previewGeneration && step === 3 && !busy && !createdId) { $('#save-draft').disabled = false; $('#save-submit').disabled = false; } })
      .catch(error => report(`No se pudo generar la vista previa: ${error.message}`, 'error'));
  }
  document.querySelector('.wizard-progress').scrollIntoView({ behavior: 'smooth', block: 'start' });
}
function invalid(message, input) {
  report(message, 'error');
  if (input) input.focus();
  return false;
}
function validateStep(index) {
  const panel = document.querySelector(`.wizard-panel[data-step="${index}"]`);
  for (const input of panel.querySelectorAll('input[required],textarea[required],select[required]')) {
    if (!input.checkValidity()) { input.reportValidity(); return invalid('Completa los campos obligatorios de este paso.', input); }
  }
  if (index === 0) {
    if (!/^\d{4}$/.test(value('year'))) return invalid('El año debe tener cuatro cifras.', field('year'));
    if (creators().some(author => !author.givenName)) return invalid('Indica el nombre de cada autor.', $('.creator-given'));
  }
  if (index === 1) {
    if (value('accessLevel') === 'EMBARGOED' && (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(value('embargoUntil')) || Number.isNaN(Date.parse(value('embargoUntil')))))
      return invalid('Indica el fin del embargo en formato UTC ISO 8601.', field('embargoUntil'));
    if (value('orcid') && !/^(?:https:\/\/orcid\.org\/)?\d{4}-\d{4}-\d{4}-[\dX]{4}$/.test(value('orcid')))
      return invalid('El ORCID no tiene un formato válido.', field('orcid'));
    if (value('ror') && !/^(?:https:\/\/ror\.org\/)?0[0-9a-hjkmnp-tv-z]{6}[0-9]{2}$/.test(value('ror')))
      return invalid('El ROR no tiene un formato válido.', field('ror'));
  }
  if (index === 2) {
    const description = $('#description-file').files[0];
    if (!description || !description.size) return invalid('Selecciona description.md o un ZIP de descripción.', $('#description-file'));
    if (description.name !== 'description.md' && !description.name.toLowerCase().endsWith('.zip'))
      return invalid('El archivo Markdown debe llamarse exactamente description.md.', $('#description-file'));
    if (!files().length) return invalid('Selecciona al menos un archivo del dataset.', $('#dataset-files'));
    const policy = policies[value('type')] || policies.OTHER;
    const paths = new Set();
    for (const file of files()) {
      if (!file.size) return invalid(`El archivo «${file.name}» está vacío.`, $('#dataset-files'));
      const path = file.webkitRelativePath || file.name;
      if (paths.has(path)) return invalid(`Hay archivos repetidos con el nombre «${path}».`, $('#dataset-files'));
      paths.add(path);
      const extension = file.name.split('.').pop().toLowerCase();
      if (policy.extensions && extension !== 'zip' && !policy.extensions.includes(extension))
        return invalid(`El tipo ${value('type')} no admite «${file.name}».`, $('#dataset-files'));
    }
  }
  return true;
}
function node(tag, text, className) {
  const element = document.createElement(tag); element.textContent = text; if (className) element.className = className; return element;
}
function previewMarkdown(text, target) {
  target.replaceChildren();
  for (const line of text.split(/\r?\n/)) {
    if (!line.trim()) continue;
    const heading = line.match(/^(#{1,3})\s+(.+)$/);
    if (heading) target.append(node(`h${heading[1].length}`, heading[2]));
    else if (/^[-*]\s+/.test(line)) target.append(node('p', `• ${line.slice(2)}`));
    else target.append(node('p', line));
  }
}
async function renderPreview() {
  const target = $('#preview-content'); target.replaceChildren();
  const identity = document.createElement('section'); identity.className = 'wizard-preview-section';
  identity.append(node('h3', value('title')), node('p', `${creators().map(author => `${author.givenName} ${author.familyName}`.trim()).join(', ')} · ${value('publisher')} · ${value('year')}`),
    node('p', `Tipo: ${value('type')} · Versión: ${value('versionLabel')} · Licencia: ${value('licenseId')}`),
    node('p', `Institución: ${value('institution')} · Acceso: ${value('accessLevel')}`),
    node('p', `Metodología: ${value('methodology')}`));
  if (value('keywords')) identity.append(node('p', `Palabras clave: ${value('keywords')}`));
  if (value('orcid')) identity.append(node('p', `ORCID: ${value('orcid')}`));
  if (value('ror')) identity.append(node('p', `ROR: ${value('ror')}`));
  if (basedOnId) identity.append(node('p', `Nueva versión de ${basedOnId}.`));
  target.append(identity);
  const description = document.createElement('section'); description.className = 'wizard-preview-section';
  description.append(node('h3', 'Descripción del proyecto'));
  const descriptionFile = $('#description-file').files[0];
  if (descriptionFile.name === 'description.md') {
    if (descriptionFile.size > 1024 * 1024) description.append(node('p', 'El Markdown supera 1 MB; se mostrará completo después de guardarlo.'));
    else { const body = node('div', '', 'wizard-markdown'); previewMarkdown(await descriptionFile.text(), body); description.append(body); }
  } else description.append(node('p', `ZIP «${descriptionFile.name}». El servidor comprobará description.md y mostrará el Markdown y sus imágenes después de guardarlo.`));
  target.append(description);
  const list = document.createElement('section'); list.className = 'wizard-preview-section'; list.append(node('h3', 'Archivos seleccionados'));
  const ul = document.createElement('ul');
  for (const file of files()) ul.append(node('li', `${file.webkitRelativePath || file.name} · ${file.size.toLocaleString('es')} bytes${file.name.toLowerCase().endsWith('.zip') ? ' · ZIP por descomprimir' : ''}`));
  list.append(ul); target.append(list);
}
async function api(path, options = {}) {
  const response = await fetch(path, { ...options, headers: auth.headers({ Accept: 'application/json', ...options.headers }) });
  const text = await response.text();
  let body; try { body = text ? JSON.parse(text) : null; } catch { body = text; }
  if (!response.ok) throw new Error(typeof body === 'string' ? body : body?.detail || body?.message || 'No se pudo completar la operación.');
  return body;
}
async function upload(path, file) {
  const body = new FormData(); body.append('file', file);
  await api(path, { method: 'POST', body });
}
function sciencePayload() {
  return Object.fromEntries(['versionLabel','licenseId','institution','ror','orcid','language','discipline','keywords','methodology','accessLevel','embargoUntil','relatedPublications'].map(name => [name, value(name)]));
}
async function save(submit) {
  if (busy || createdId) return;
  if (!basedOnVerified) { report('La versión anterior debe ser publicada y tuya antes de crear una sucesora.', 'error'); return; }
  for (const index of [0,1,2]) {
    if (!validateStep(index)) { showStep(index); validateStep(index); return; }
  }
  busy = true; $('#wizard-back').disabled = true; $('#save-draft').disabled = true; $('#save-submit').disabled = true;
  const payload = { titles: [{ value: value('title') }], publisher: value('publisher'), publicationYear: value('year'), resourceType: { value: value('type'), typeGeneral: value('type') }, creators: creators() };
  try {
    report('Creando borrador…');
    const created = await api('/api/v1/dataresources/', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
    createdId = created.id;
    let inheritedConceptualDoi = null;
    if (basedOnId) {
      report('Enlazando nueva versión…');
      const derived = await api(`/api/v1/scientific/${encodeURIComponent(createdId)}/derive-from/${encodeURIComponent(basedOnId)}`, { method: 'POST' });
      inheritedConceptualDoi = derived.conceptualDoi;
    }
    report('Guardando ficha científica…');
    await api(`/api/v1/scientific/${encodeURIComponent(createdId)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...sciencePayload(), conceptualDoi: inheritedConceptualDoi }) });
    report('Subiendo descripción…');
    await upload(`/api/v1/dataresources/${encodeURIComponent(createdId)}/description`, $('#description-file').files[0]);
    for (const [index, file] of files().entries()) {
      report(`Subiendo archivo ${index + 1} de ${files().length}: ${file.name}…`);
      await upload(`/api/v1/dataresources/${encodeURIComponent(createdId)}/attachments?path=${encodeURIComponent(file.webkitRelativePath || file.name)}`, file);
    }
    if (submit) { report('Validando y enviando a revisión…'); await api(`/api/v1/scientific/${encodeURIComponent(createdId)}/submit`, { method: 'POST' }); }
    toast.success(submit ? 'Depósito enviado a revisión.' : 'Borrador guardado.');
    busy = false;
    location.assign(`resource.html?id=${encodeURIComponent(createdId)}`);
  } catch (error) {
    report(createdId ? `El borrador ${createdId} se creó, pero no se completó el proceso: ${error.message} Ábrelo para corregirlo; no se enviará automáticamente.` : error.message, 'error');
    toast.error(error.message);
    if (createdId) {
      const link = document.createElement('a'); link.href = `resource.html?id=${encodeURIComponent(createdId)}`; link.textContent = 'Abrir borrador y corregirlo';
      errorBox.append(document.createElement('br'), link);
    } else { $('#wizard-back').disabled = false; $('#save-draft').disabled = false; $('#save-submit').disabled = false; }
    busy = false;
  }
}

form.addEventListener('submit', event => {
  event.preventDefault();
  if (busy) return;
  if (step < 3) { if (validateStep(step)) showStep(step + 1); }
  else if (!$('#save-draft').disabled) save(false);
});
$('#add-creator').addEventListener('click', () => addCreator());
field('type').addEventListener('change', applyPolicy);
$('#dataset-files').addEventListener('change', updateFileCount);
$('#wizard-back').addEventListener('click', () => showStep(step - 1));
$('#wizard-next').addEventListener('click', () => { if (validateStep(step)) showStep(step + 1); });
$('#save-draft').addEventListener('click', () => save(false));
$('#save-submit').addEventListener('click', () => save(true));
window.addEventListener('beforeunload', event => { if (busy && createdId) { event.preventDefault(); event.returnValue = ''; } });
addCreator(); field('year').value = String(new Date().getFullYear()); field('publisher').value = 'Repositorio local'; applyPolicy(); showStep(0);
if (basedOnId) Promise.all([
  api(`/api/v1/dataresources/${encodeURIComponent(basedOnId)}`),
  api(`/api/v1/scientific/${encodeURIComponent(basedOnId)}`),
  api('/api/v1/my-dataresources')
]).then(([base, previous, mine]) => {
  if (previous.status !== 'PUBLISHED' || !mine.some(item => item.id === basedOnId))
    throw new Error('Solo puedes derivar una versión de un depósito propio y publicado.');
  basedOnVerified = true;
  field('title').value = base.titles?.[0]?.value || '';
  field('year').value = base.publicationYear || field('year').value;
  field('publisher').value = base.publisher || '';
  field('type').value = base.resourceType?.typeGeneral || 'OTHER';
  $('#creator-list').replaceChildren(); for (const author of base.creators || []) addCreator(author.givenName || '', author.familyName || '');
  if (!$('#creator-list').childElementCount) addCreator();
  applyPolicy();
}).catch(error => report(`No se pudo cargar la versión anterior: ${error.message}`, 'error'));
