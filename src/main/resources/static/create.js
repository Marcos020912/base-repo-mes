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
const packageMode = () => field('uploadMode').value === 'package';
const errorBox = $('#wizard-message');
const translationEditor=metadataTranslations.mount($('#metadata-translations'),field('translations'));
const autosaveKey = `reduniv-deposit-v1:${user?.username || 'anonymous'}:${basedOnId || 'new'}`;
const autosaveFields = ['title','year','type','publisher','versionLabel','licenseId','institution','ror','orcid','language','discipline','keywords','methodology','productionDescription','processingDescription','processingTools','summary','temporalStart','temporalEnd','geographicCoverage','translations','accessLevel','embargoUntil','relatedPublications','privacyClassification','profileSnapshot'];
let previewReady=false;
let autosaveEnabled = false;
let autosaveTimer;
function storedDraft() {
  try {
    const draft = JSON.parse(localStorage.getItem(autosaveKey) || 'null');
    return draft && Date.now() - draft.updatedAt < 14 * 86400000 ? draft : null;
  } catch { return null; }
}
function persistDraft() {
  if (!autosaveEnabled || createdId) return;
  const values = Object.fromEntries(autosaveFields.map(name => [name, value(name)]));
  try {
    localStorage.setItem(autosaveKey, JSON.stringify({ values, creators: creators(), funding: fundingEntries(), relations: relationEntries(), uploadMode: packageMode() ? 'package' : 'separate', step, updatedAt: Date.now() }));
    $('#autosave-status').textContent = 'Metadatos guardados en este navegador. Los archivos deben seleccionarse de nuevo tras recargar.';
    $('#discard-autosave').hidden = false;
  } catch { $('#autosave-status').textContent = 'No se pudieron guardar los metadatos en este navegador.'; }
}
function scheduleAutosave() {
  if (!autosaveEnabled || createdId) return;
  clearTimeout(autosaveTimer);
  autosaveTimer = setTimeout(persistDraft, 500);
}
function restoreDraft() {
  const draft = storedDraft();
  if (draft) {
    for (const [name, saved] of Object.entries(draft.values || {})) {
      if (autosaveFields.includes(name) && typeof saved === 'string') field(name).value = saved;
    }
    translationEditor.set(JSON.parse(value('translations')||'{}'));
    if (Array.isArray(draft.creators) && draft.creators.length) {
      $('#creator-list').replaceChildren();
      for (const author of draft.creators) addCreator(author.givenName || '', author.familyName || '');
    }
    if(Array.isArray(draft.relations)){relationRows.replaceChildren();draft.relations.slice(0,30).forEach(addRelation);}
    if (Array.isArray(draft.funding)) {
      $('#funding-list').replaceChildren();
      for (const item of draft.funding.slice(0, 20)) addFunding(item);
    }
    if (draft.uploadMode === 'package') form.querySelector('[name="uploadMode"][value="package"]').checked = true;
    field('privacyClassification').dispatchEvent(new Event('change'));
    updateUploadMode();
    $('#autosave-status').textContent = 'Se recuperaron tus metadatos. Selecciona de nuevo el ZIP o los archivos antes de guardar.';
    $('#discard-autosave').hidden = false;
  }
  autosaveEnabled = true;
  applyPolicy();
}

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
function addFunding(item = {}) {
  if ($('#funding-list').childElementCount >= 20) return;
  const row = document.createElement('div'); row.className = 'related-editor-row funding-row';
  for (const [name, label, max] of [['funderName','Financiador',255],['funderRor','ROR del financiador',255],
      ['awardNumber','Número de subvención',100],['awardTitle','Nombre del proyecto',500]]) {
    const wrapper = document.createElement('label'); wrapper.textContent = label;
    const input = document.createElement('input'); input.name = `funding-${name}`; input.maxLength = max;
    input.value = item[name] || ''; input.required = name === 'funderName'; wrapper.append(input); row.append(wrapper);
  }
  const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'secondary';
  remove.textContent = 'Quitar'; remove.onclick = () => { row.remove(); scheduleAutosave(); };
  row.append(remove); $('#funding-list').append(row);
}
// Relaciones tipadas: selección explícita, sin inferir DOI ni descargar enlaces externos.
const relationKinds=['ARTICLE','SOFTWARE','DATASET','PROJECT','OTHER'];
const relationTypes=['IsCitedBy','Cites','IsSupplementTo','IsSupplementedBy','IsReferencedBy','References','IsDocumentedBy','Documents','IsDerivedFrom','IsSourceOf','IsPartOf','HasPart'];
const relationRows=document.createElement('div');relationRows.id='creation-relations';relationRows.className='related-editor';
const relationAdd=document.createElement('button');relationAdd.type='button';relationAdd.className='secondary';relationAdd.textContent='+ Añadir artículo, software o proyecto';
const relationHeading=document.createElement('h3');relationHeading.textContent='Relaciones científicas tipadas';
const relationPanel=document.querySelector('.wizard-panel[data-step="5"] .wizard-fields');relationPanel.append(relationHeading,relationRows,relationAdd);
function addRelation(data={}){
  if(relationRows.childElementCount>=30)return;
  const row=document.createElement('div');row.className='related-editor-row';
  for(const [name,label,choices,max] of [['kind','Tipo de recurso',relationKinds],['identifierType','Tipo de identificador',['DOI','URL']],['relationType','Relación DataCite',relationTypes],['identifier','DOI o URL HTTPS',null,500],['title','Título',null,255]]){
    const wrapper=document.createElement('label');wrapper.textContent=label;const input=document.createElement(choices?'select':'input');input.dataset.relationField=name;
    if(choices)input.required=true;
    if(choices)for(const choice of choices){const option=document.createElement('option');option.value=choice;option.textContent=choice;input.append(option);}
    else{input.maxLength=max;input.required=name==='identifier';}
    input.value=typeof data[name]==='string'?data[name]:choices?.[0]||'';wrapper.append(input);row.append(wrapper);
  }
  const remove=document.createElement('button');remove.type='button';remove.className='danger-outline';remove.textContent='Quitar relación';remove.onclick=()=>{row.remove();scheduleAutosave();};row.append(remove);relationRows.append(row);
}
relationAdd.onclick=()=>{addRelation();scheduleAutosave();};
function relationEntries(){return [...relationRows.children].map(row=>Object.fromEntries([...row.querySelectorAll('[data-relation-field]')].map(input=>[input.dataset.relationField,input.value.trim()])));}
function validRelations(){
  const seen=new Set();for(const row of relationRows.children){
    const values=Object.fromEntries([...row.querySelectorAll('[data-relation-field]')].map(input=>[input.dataset.relationField,input.value.trim()]));let identifier=values.identifier;
    if(values.identifierType==='DOI'){identifier=identifier.replace(/^https?:\/\/(?:dx\.)?doi\.org\//i,'');if(!/^10\.\d{4,9}\/\S+$/i.test(identifier))return invalid('DOI relacionado no válido.',row.querySelector('[data-relation-field=identifier]'));}
    else{try{const uri=new URL(identifier);if(uri.protocol!=='https:'||uri.username||uri.password)throw Error();}catch{return invalid('La URL relacionada debe ser HTTPS válida.',row.querySelector('[data-relation-field=identifier]'));}}
    const key=values.identifierType+':'+identifier.toLowerCase()+':'+values.relationType;if(seen.has(key))return invalid('Relación duplicada.',row.querySelector('[data-relation-field=identifier]'));seen.add(key);
  }return true;
}
function fundingEntries() {
  return [...document.querySelectorAll('.funding-row')].map(row => Object.fromEntries(
    [...row.querySelectorAll('[name]')].map(input => [input.name.replace(/^funding-/, ''), input.value.trim()])));
}
function applyPolicy() {
  const policy = policies[value('type')] || policies.OTHER;
  $('#dataset-files').accept = policy.accept ? `${policy.accept},.zip` : '';
  $('#file-type-hint').textContent = `${policy.hint}. También se permite un ZIP validado por el servidor.`;
  updateFileCount();
}
function updateUploadMode() {
  const packaged = packageMode();
  $('#separate-upload').hidden = packaged;
  $('#package-upload').hidden = !packaged;
  $('#description-file').required = !packaged;
  $('#dataset-files').required = !packaged;
  $('#package-file').required = packaged;
  updateFileCount();
}
function updateFileCount() {
  updateSubmissionButton();
  if (packageMode()) {
    const archive = $('#package-file').files[0];
    $('#file-count').textContent = archive ? `${archive.name} · ${archive.size.toLocaleString('es')} bytes. El contenido se validará al guardar.` : 'Aún no hay un ZIP seleccionado.';
    return;
  }
  const selected = files();
  $('#file-count').textContent = selected.length ? `${selected.length} archivo(s), ${selected.reduce((total, file) => total + file.size, 0).toLocaleString('es')} bytes en total.` : 'Aún no hay archivos seleccionados.';
}
function renderLocalQuality(){
  const checks=[
    ['Título',Boolean(value('title').trim()),'Identifica el depósito.'],
    ['Autoría',creators().length>0&&creators().every(author=>author.givenName),'Atribuye la producción; un nombre no verifica identidad.'],
    ['Versión',Boolean(value('versionLabel').trim()),'Distingue el contenido que se citará.'],
    ['Licencia',Boolean(value('licenseId').trim()),'Declara condiciones de reutilización; la curación debe revisarlas.'],
    ['Institución',Boolean(value('institution').trim()),'Indica responsabilidad institucional.'],
    ['Metodología',Boolean(value('methodology').trim()),'Explica producción y reutilización.'],
    ['Documentación seleccionada',Boolean(packageMode()?$('#package-file').files[0]:$('#description-file').files[0]),'Se comprobará description.md en el servidor al guardar.'],
    ['Contenido seleccionado',Boolean(packageMode()?$('#package-file').files[0]:files().length),'Tipos, rutas y archivos internos de ZIP se validarán en el servidor.']
  ];
  for(const item of metadataProfileWidget.missing())checks.push(['Perfil: '+item.label,false,'Requisito adicional de la revisión de perfil elegida.']);
  const container=$('#wizard-quality');container.replaceChildren(node('p',`${checks.filter(check=>check[1]).length} de ${checks.length} comprobaciones locales completas. No equivale a aprobación ni a integridad verificada.`));
  const list=document.createElement('ul');for(const [label,complete,why]of checks){const entry=node('li',`${complete?'✓ Selección presente':'✕ Falta completar'}: ${label}. ${why}`);list.append(entry);}container.append(list);
  if(needsPostUploadPreview())container.append(node('p','El ZIP no queda aprobado por esta precomprobación. Al guardarlo deberá revisar la descripción y los archivos realmente extraídos antes de enviar a curación.'));
}
function needsPostUploadPreview() {
  return packageMode() || $('#description-file').files[0]?.name.toLowerCase().endsWith('.zip') || files().some(file => file.name.toLowerCase().endsWith('.zip'));
}
function updateSubmissionButton() {
  $('#save-submit').textContent = needsPostUploadPreview() ? 'Guardar y revisar antes de enviar' : 'Guardar y enviar a revisión';
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
  $('#wizard-next').hidden = step === 8;
  $('#wizard-next').disabled=false;
  $('#save-draft').hidden = step !== 8;
  $('#save-submit').hidden = step !== 8;
  report('');
  if(step===6)renderLocalQuality();
  if (step === 7) {
    previewReady=false;$('#wizard-next').disabled=true;
    renderPreview().then(() => { if (generation === previewGeneration && step === 7 && !busy && !createdId) { previewReady=true;$('#wizard-next').disabled=false; } })
      .catch(error => report(`No se pudo generar la vista previa: ${error.message}`, 'error'));
  }
  if(step===8){$('#save-draft').disabled=busy;$('#save-submit').disabled=busy||!previewReady;}
  document.querySelector('.wizard-progress').scrollIntoView({ behavior: 'instant', block: 'start' });
  const heading=document.querySelector(`.wizard-panel[data-step="${step}"] h2`);heading.tabIndex=-1;heading.focus({preventScroll:true});
  scheduleAutosave();
}
function invalid(message, input) {
  report(message, 'error');
  if (input) input.focus();
  return false;
}
function validateStep(index) {
  if(index===5&&!validRelations())return false;
  field('temporalEnd').setCustomValidity(value('temporalStart') && value('temporalEnd') && value('temporalStart')>value('temporalEnd')?'El fin de cobertura no puede ser anterior al inicio.':'');
  const panel = document.querySelector(`.wizard-panel[data-step="${index}"]`);
  for (const input of panel.querySelectorAll('input:not([type=hidden]),textarea,select')) {
    if (!input.checkValidity()) { input.reportValidity(); return invalid('Completa los campos obligatorios de este paso.', input); }
  }
  if (index === 0) {
    if (!/^\d{4}$/.test(value('year'))) return invalid('El año debe tener cuatro cifras.', field('year'));
  }
  if (index === 1) {
    if (creators().some(author => !author.givenName)) return invalid('Indica el nombre de cada autor.', $('.creator-given'));
    if (value('orcid') && !/^(?:https:\/\/orcid\.org\/)?\d{4}-\d{4}-\d{4}-[\dX]{4}$/.test(value('orcid')))
      return invalid('El ORCID no tiene un formato válido.', field('orcid'));
    if (value('ror') && !/^(?:https:\/\/ror\.org\/)?0[0-9a-hjkmnp-tv-z]{6}[0-9]{2}$/.test(value('ror')))
      return invalid('El ROR no tiene un formato válido.', field('ror'));
  }
  if(index===5){
    for (const row of document.querySelectorAll('.funding-row')) {
      const funderRor = row.querySelector('[name="funding-funderRor"]');
      if (funderRor.value.trim() && !/^(?:https:\/\/ror\.org\/)?0[0-9a-hjkmnp-tv-z]{6}[0-9]{2}$/.test(funderRor.value.trim()))
        return invalid('El ROR del financiador no tiene un formato válido.', funderRor);
    }
  }
  if (index === 3) {
    if (packageMode()) {
      const archive = $('#package-file').files[0];
      if (!archive || !archive.size) return invalid('Selecciona un ZIP completo del depósito.', $('#package-file'));
      if (!archive.name.toLowerCase().endsWith('.zip')) return invalid('El depósito completo debe ser un archivo ZIP.', $('#package-file'));
      return true;
    }
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
  if(index===4){
    if(['PERSONAL','CONFIDENTIAL'].includes(value('privacyClassification'))&&value('accessLevel')!=='RESTRICTED')return invalid('Los datos personales/confidenciales necesitan acceso restringido.',field('accessLevel'));
    if(value('accessLevel')==='EMBARGOED'&&(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(value('embargoUntil'))||Number.isNaN(Date.parse(value('embargoUntil')))))return invalid('Indica el fin del embargo en formato UTC ISO 8601.',field('embargoUntil'));
  }
  if(index===6){const missing=metadataProfileWidget.missing();if(missing.length){const input=field(missing[0].code);if(!input)return invalid('El perfil guardado contiene reglas no reconocidas; vuelva a seleccionar un perfil aprobado.');showStep(Number(input.closest('.wizard-panel').dataset.step));return invalid('El perfil requiere: '+missing[0].label,input);}}
  if(index===7&&!previewReady)return invalid('Espere a que se complete la vista previa o vuelva a generarla.');
  if(index===8&&!field('reviewConfirmed').checked)return invalid('Confirme que revisó la ficha, la cita y los archivos.',field('reviewConfirmed'));
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
  const appliedProfile=metadataProfileWidget.snapshot();if(appliedProfile)identity.append(node('p',`Perfil: ${metadataProfileWidget.title(appliedProfile)} · revisión ${appliedProfile.revision??'heredada'} · reglas: ${metadataProfileWidget.fields(appliedProfile).join(', ')||'solo base'}`));
  if (value('keywords')) identity.append(node('p', `Palabras clave: ${value('keywords')}`));
  if (value('orcid')) identity.append(node('p', `ORCID: ${value('orcid')}`));
  if (value('ror')) identity.append(node('p', `ROR: ${value('ror')}`));
  for (const item of fundingEntries()) identity.append(node('p', `Financiación: ${item.funderName}${item.awardTitle ? ` · ${item.awardTitle}` : ''}${item.awardNumber ? ` (${item.awardNumber})` : ''}`));
  if (basedOnId) identity.append(node('p', `Nueva versión de ${basedOnId}.`));
  if(value('privacyClassification'))identity.append(node('p',`Privacidad declarada: ${value('privacyClassification')} · la nota se conserva únicamente en evaluación privada.`));
  for(const [name,label]of [['productionDescription','Producción y origen'],['processingDescription','Procesamiento'],['processingTools','Herramientas/versiones']])if(value(name))identity.append(node('p',`${label}: ${value(name)}`));
  if(value('summary'))identity.append(node('p',`Resumen: ${value('summary')}`));
  if(value('geographicCoverage'))identity.append(node('p',`Cobertura geográfica: ${value('geographicCoverage')}`));
  if(value('temporalStart')||value('temporalEnd'))identity.append(node('p',`Cobertura temporal: ${value('temporalStart')||'Sin inicio'} / ${value('temporalEnd')||'Sin fin'}`));
  for(const [lang,item] of Object.entries(translationEditor.get()))identity.append(node('p',`${lang}: ${item.title||''} · ${item.summary||''}`));
  target.append(identity);
  const citationPreview=document.createElement('section');citationPreview.className='wizard-preview-section';
  citationPreview.append(node('h3','Vista previa de cita (borrador)'));
  const authorText=creators().map(author=>[author.familyName,author.givenName].filter(Boolean).join(', ')).join('; ') || 'Autoría pendiente';
  const draftCitation=`${authorText} (${value('year') || 's. f.'}). ${value('title')} (versión ${value('versionLabel') || 'pendiente'}) [${value('type')}]. ${value('publisher') || 'Editorial pendiente'}.`;
  citationPreview.append(node('p',draftCitation),node('p','Cita provisional: este depósito todavía no está publicado. El DOI resoluble y la landing permanente se incorporan después de la aprobación; no cite esta vista previa como una publicación.','muted'));
  target.append(citationPreview);
  if(relationEntries().length){const section=node("section","","wizard-preview-section");section.append(node("h3","Recursos relacionados"));for(const item of relationEntries())section.append(node("p",`${item.kind} · ${item.relationType}: ${item.title || item.identifier} (${item.identifier})`));target.append(section);}
  const description = document.createElement('section'); description.className = 'wizard-preview-section';
  description.append(node('h3', 'Descripción del proyecto'));
  if (packageMode()) {
    const archive = $('#package-file').files[0];
    description.append(node('p', `ZIP completo «${archive.name}». Debe contener description/description.md, imágenes opcionales en description/ y archivos ${value('type')} en la raíz. El servidor comprobará su contenido antes de guardarlo.`));
    target.append(description);
    const list = document.createElement('section'); list.className = 'wizard-preview-section'; list.append(node('h3', 'Paquete seleccionado'));
    list.append(node('p', `${archive.name} · ${archive.size.toLocaleString('es')} bytes`));
    target.append(list);
    return;
  }
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
  await transfers.upload(path, file);
}
function sciencePayload() {
  const payload=Object.fromEntries(['versionLabel','licenseId','institution','ror','orcid','language','discipline','keywords','methodology','productionDescription','processingDescription','processingTools','summary','temporalStart','temporalEnd','geographicCoverage','translations','accessLevel','embargoUntil','relatedPublications'].map(name => [name, value(name)]));
  payload.translations=translationEditor.get();return payload;
}
async function save(submit) {
  if (busy || createdId) return;
  const reviewAfterUpload = submit && needsPostUploadPreview();
  if (!basedOnVerified) { report('La versión anterior debe ser publicada y tuya antes de crear una sucesora.', 'error'); return; }
  if(submit&&!validateStep(8))return;
  for (const index of [0,1,2,3,4,5,6]) {
    if (!validateStep(index)) { showStep(index); validateStep(index); return; }
  }
  busy = true; $('#wizard-back').disabled = true; $('#save-draft').disabled = true; $('#save-submit').disabled = true;
  const payload = { titles: [{ value: value('title') }], publisher: value('publisher'), publicationYear: value('year'), resourceType: { value: value('type'), typeGeneral: value('type') }, creators: creators() };
  try {
    report('Creando borrador…');
    const created = await api('/api/v1/dataresources/', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
    createdId = created.id;
    clearTimeout(autosaveTimer);
    try { localStorage.removeItem(autosaveKey); } catch { /* Storage may be disabled. */ }
    let inheritedConceptualDoi = null;
    if (basedOnId) {
      report('Enlazando nueva versión…');
      const derived = await api(`/api/v1/scientific/${encodeURIComponent(createdId)}/derive-from/${encodeURIComponent(basedOnId)}`, { method: 'POST' });
      inheritedConceptualDoi = derived.conceptualDoi;
    }
    report('Guardando ficha científica…');
    const savedScience=await api(`/api/v1/scientific/${encodeURIComponent(createdId)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...sciencePayload(), conceptualDoi: inheritedConceptualDoi }) });
    await metadataProfileWidget.save(createdId,savedScience.revision);
    await scientificPrivacy.saveWizard(createdId);
    if(relationEntries().length)await api(`/api/v1/scientific/${encodeURIComponent(createdId)}/relations`,{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(relationEntries())});
    if (fundingEntries().length) {
      report('Guardando financiación…');
      await api(`/api/v1/scientific/${encodeURIComponent(createdId)}/funding`, { method: 'PUT',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fundingEntries()) });
    }
    if (packageMode()) {
      const archive = $('#package-file').files[0];
      report(`Validando y subiendo paquete «${archive.name}»…`);
      await upload(`/api/v1/dataresources/${encodeURIComponent(createdId)}/attachments?package=true&path=${encodeURIComponent(archive.name)}`, archive);
    } else {
      report('Subiendo descripción…');
      await upload(`/api/v1/dataresources/${encodeURIComponent(createdId)}/description`, $('#description-file').files[0]);
      for (const [index, file] of files().entries()) {
        report(`Subiendo archivo ${index + 1} de ${files().length}: ${file.name}…`);
        await upload(`/api/v1/dataresources/${encodeURIComponent(createdId)}/attachments?path=${encodeURIComponent(file.webkitRelativePath || file.name)}`, file);
      }
    }
    if (submit && !reviewAfterUpload) { report('Validando y enviando a revisión…'); await api(`/api/v1/scientific/${encodeURIComponent(createdId)}/submit`, { method: 'POST' }); }
    toast.success(reviewAfterUpload ? 'Borrador guardado. Revisa la descripción y los archivos antes de enviarlo.' : submit ? 'Depósito enviado a revisión.' : 'Borrador guardado.');
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
  if (step < 8) { if (validateStep(step)) showStep(step + 1); }
  else if (!$('#save-draft').disabled) save(false);
});
$('#add-creator').addEventListener('click', () => addCreator());
$('#add-funding').addEventListener('click', () => { addFunding(); scheduleAutosave(); });
form.addEventListener('input', scheduleAutosave);
form.addEventListener('change', scheduleAutosave);
form.addEventListener('click', event => { if (event.target.closest('#add-creator,.creator-row button')) scheduleAutosave(); });
$('#discard-autosave').addEventListener('click', () => {
  clearTimeout(autosaveTimer);
  try { localStorage.removeItem(autosaveKey); } catch { /* Storage may be disabled. */ }
  $('#discard-autosave').hidden = true;
  $('#autosave-status').textContent = 'Datos locales descartados; los campos actuales permanecen hasta recargar.';
});
field('type').addEventListener('change', applyPolicy);
$('#dataset-files').addEventListener('change', updateFileCount);
$('#description-file').addEventListener('change', updateSubmissionButton);
$('#package-file').addEventListener('change', updateFileCount);
form.querySelectorAll('[name="uploadMode"]').forEach(input => input.addEventListener('change', updateUploadMode));
$('#retry-preview').addEventListener('click',()=>showStep(7));
$('#wizard-back').addEventListener('click', () => showStep(step - 1));
$('#wizard-next').addEventListener('click', () => { if (validateStep(step)) showStep(step + 1); });
$('#save-draft').addEventListener('click', () => save(false));
$('#save-submit').addEventListener('click', () => save(true));
window.addEventListener('beforeunload', event => { if (busy && createdId) { event.preventDefault(); event.returnValue = ''; } });
addCreator(); field('year').value = String(new Date().getFullYear()); field('publisher').value = 'Repositorio local'; updateUploadMode(); applyPolicy(); showStep(0);
if (!basedOnId) {restoreDraft();metadataProfileWidget.refresh();}
if (basedOnId) Promise.all([
  api(`/api/v1/dataresources/${encodeURIComponent(basedOnId)}`),
  api(`/api/v1/scientific/${encodeURIComponent(basedOnId)}`),
  api('/api/v1/my-dataresources'),
  api(`/api/v1/scientific/${encodeURIComponent(basedOnId)}/funding`),
  api(`/api/v1/scientific/${encodeURIComponent(basedOnId)}/relations`)
]).then(([base, previous, mine, previousFunding, previousRelations]) => {
  if (previous.status !== 'PUBLISHED' || !mine.some(item => item.id === basedOnId))
    throw new Error('Solo puedes derivar una versión de un depósito propio y publicado.');
  basedOnVerified = true;
  field('title').value = base.titles?.[0]?.value || '';
  field('year').value = base.publicationYear || field('year').value;
  field('publisher').value = base.publisher || '';
  field('type').value = base.resourceType?.typeGeneral || 'OTHER';
  $('#creator-list').replaceChildren(); for (const author of base.creators || []) addCreator(author.givenName || '', author.familyName || '');
  if (!$('#creator-list').childElementCount) addCreator();
  for (const name of ['productionDescription','processingDescription','processingTools','summary','temporalStart','temporalEnd','geographicCoverage'])field(name).value=previous[name]||'';
  translationEditor.set(previous.translations||{});
  for (const item of previousFunding) addFunding(item);
  for(const item of previousRelations)addRelation(item);
  applyPolicy();
  metadataProfileWidget.inherit(previous);
  restoreDraft();metadataProfileWidget.refresh();
}).catch(error => report(`No se pudo cargar la versión anterior: ${error.message}`, 'error'));
