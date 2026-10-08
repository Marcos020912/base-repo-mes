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
  IMAGE: { accept: '.jpg,.jpeg,.png,.gif,.webp,.svg,.tif,.tiff,.bmp', extensions: ['jpg','jpeg','png','gif','webp','svg','tif','tiff','bmp'] },
  TEXT: { accept: '.pdf,.doc,.docx,.odt,.rtf,.txt,.md,.epub', extensions: ['pdf','doc','docx','odt','rtf','txt','md','epub'] },
  AUDIOVISUAL: { accept: '.mp4,.webm,.mov,.avi,.mkv,.mpeg,.mpg,.m4v', extensions: ['mp4','webm','mov','avi','mkv','mpeg','mpg','m4v'] },
  DATASET: { accept: '.csv,.tsv,.tab,.xls,.xlsx,.ods,.parquet,.sav,.dta,.json,.xml', extensions: ['csv','tsv','tab','xls','xlsx','ods','parquet','sav','dta','json','xml'] },
  OTHER: { accept: '', extensions: null }
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
    uiI18n.set($('#autosave-status'),'wizard.autosaved');
    $('#discard-autosave').hidden = false;
  } catch { uiI18n.set($('#autosave-status'),'wizard.autosaveFailed'); }
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
    uiI18n.set($('#autosave-status'),'wizard.recovered');
    $('#discard-autosave').hidden = false;
  }
  autosaveEnabled = true;
  applyPolicy();
}

function report(message,kind=''){errorBox.className=`message ${kind}`;uiI18n.plain(errorBox,message);}
function reportKey(key,kind='',params={}){errorBox.className=`message ${kind}`;uiI18n.plain(errorBox,'');errorBox.append(uiNode('span',key,params));}
function reportFailure(key,error,params={}){errorBox.className='message error';uiI18n.plain(errorBox,'');if(key)errorBox.append(uiNode('span',key,params),document.createTextNode(' '));const detail=document.createElement('span');uiI18n.showError(detail,error);errorBox.append(detail);}
function addCreator(givenName = '', familyName = '') {
  const row = document.createElement('div'); row.className = 'creator-row';
  const given = document.createElement('label'); given.append(uiNode('span','wizard.givenName'));
  const givenInput = document.createElement('input'); givenInput.className = 'creator-given'; givenInput.required = true; givenInput.maxLength = 255; givenInput.value = givenName; given.append(givenInput);
  const family = document.createElement('label'); family.append(uiNode('span','wizard.familyName'));
  const familyInput = document.createElement('input'); familyInput.className = 'creator-family'; familyInput.maxLength = 255; familyInput.value = familyName; family.append(familyInput);
  const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'secondary'; uiI18n.set(remove,'wizard.remove');
  remove.addEventListener('click', () => { if ($('#creator-list').childElementCount > 1) row.remove(); else reportKey('wizard.keepAuthor', 'error'); });
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
  for (const [name, label, max] of [['funderName','wizard.funder',255],['funderRor','wizard.funderRor',255],
      ['awardNumber','wizard.awardNumber',100],['awardTitle','wizard.projectName',500]]) {
    const wrapper = document.createElement('label');wrapper.append(uiNode('span',label));
    const input = document.createElement('input'); input.name = `funding-${name}`; input.maxLength = max;
    input.value = item[name] || ''; input.required = name === 'funderName'; wrapper.append(input); row.append(wrapper);
  }
  const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'secondary';
  uiI18n.set(remove,'wizard.remove'); remove.onclick = () => { row.remove(); scheduleAutosave(); };
  row.append(remove); $('#funding-list').append(row);
}
// Relaciones tipadas: selección explícita, sin inferir DOI ni descargar enlaces externos.
const relationKinds=['ARTICLE','SOFTWARE','DATASET','PROJECT','OTHER'];
const relationTypes=['IsCitedBy','Cites','IsSupplementTo','IsSupplementedBy','IsReferencedBy','References','IsDocumentedBy','Documents','IsDerivedFrom','IsSourceOf','IsPartOf','HasPart'];
const relationRows=document.createElement('div');relationRows.id='creation-relations';relationRows.className='related-editor';
const relationAdd=document.createElement('button');relationAdd.type='button';relationAdd.className='secondary';uiI18n.set(relationAdd,'wizard.addRelation');
const relationHeading=document.createElement('h3');uiI18n.set(relationHeading,'wizard.typedRelations');
const relationPanel=document.querySelector('.wizard-panel[data-step="5"] .wizard-fields');relationPanel.append(relationHeading,relationRows,relationAdd);
function addRelation(data={}){
  if(relationRows.childElementCount>=30)return;
  const row=document.createElement('div');row.className='related-editor-row';
  for(const [name,label,choices,max] of [['kind','wizard.contentType',relationKinds],['identifierType','wizard.identifierType',['DOI','URL']],['relationType','wizard.dataCiteRelation',relationTypes],['identifier','wizard.httpsDoi',null,500],['title','wizard.title',null,255]]){
    const wrapper=document.createElement('label');wrapper.append(uiNode('span',label));const input=document.createElement(choices?'select':'input');input.dataset.relationField=name;
    if(choices)input.required=true;
    if(choices)for(const choice of choices){const option=document.createElement('option');option.value=choice;option.textContent=choice;input.append(option);}
    else{input.maxLength=max;input.required=name==='identifier';}
    input.value=typeof data[name]==='string'?data[name]:choices?.[0]||'';wrapper.append(input);row.append(wrapper);
  }
  const remove=document.createElement('button');remove.type='button';remove.className='danger-outline';uiI18n.set(remove,'wizard.removeRelation');remove.onclick=()=>{row.remove();scheduleAutosave();};row.append(remove);relationRows.append(row);
}
relationAdd.onclick=()=>{addRelation();scheduleAutosave();};
function relationEntries(){return [...relationRows.children].map(row=>Object.fromEntries([...row.querySelectorAll('[data-relation-field]')].map(input=>[input.dataset.relationField,input.value.trim()])));}
function validRelations(){
  const seen=new Set();for(const row of relationRows.children){
    const values=Object.fromEntries([...row.querySelectorAll('[data-relation-field]')].map(input=>[input.dataset.relationField,input.value.trim()]));let identifier=values.identifier;
    if(values.identifierType==='DOI'){identifier=identifier.replace(/^https?:\/\/(?:dx\.)?doi\.org\//i,'');if(!/^10\.\d{4,9}\/\S+$/i.test(identifier))return invalidKey('wizard.invalidRelatedDoi',row.querySelector('[data-relation-field=identifier]'));}
    else{try{const uri=new URL(identifier);if(uri.protocol!=='https:'||uri.username||uri.password)throw Error();}catch{return invalidKey('wizard.invalidRelatedUrl',row.querySelector('[data-relation-field=identifier]'));}}
    const key=values.identifierType+':'+identifier.toLowerCase()+':'+values.relationType;if(seen.has(key))return invalidKey('wizard.duplicateRelation',row.querySelector('[data-relation-field=identifier]'));seen.add(key);
  }return true;
}
function fundingEntries() {
  return [...document.querySelectorAll('.funding-row')].map(row => Object.fromEntries(
    [...row.querySelectorAll('[name]')].map(input => [input.name.replace(/^funding-/, ''), input.value.trim()])));
}
function applyPolicy() {
  const policy = policies[value('type')] || policies.OTHER;
  $('#dataset-files').accept = policy.accept ? `${policy.accept},.zip` : '';
  uiI18n.set($('#file-type-hint'),`wizard.hint.${Object.hasOwn(policies,value('type'))?value('type'):'OTHER'}`);
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
function updateFileCount(){updateSubmissionButton();if(packageMode()){const archive=$('#package-file').files[0];uiI18n.set($('#file-count'),archive?'wizard.zipSelected':'wizard.noZip',{name:archive?.name,size:archive?.size.toLocaleString(uiI18n.locale)});return;}const selected=files();uiI18n.set($('#file-count'),selected.length?'wizard.filesSelected':'wizard.noFiles',{count:selected.length,size:selected.reduce((total,file)=>total+file.size,0).toLocaleString(uiI18n.locale)});}
function renderLocalQuality(){
  const checks=[['wizard.title',Boolean(value('title').trim()),'wizard.local.title'],['quality.authors',creators().length>0&&creators().every(author=>author.givenName),'wizard.local.authors'],['wizard.version',Boolean(value('versionLabel').trim()),'wizard.local.version'],['wizard.license',Boolean(value('licenseId').trim()),'wizard.local.license'],['wizard.institution',Boolean(value('institution').trim()),'wizard.local.institution'],['wizard.methodology',Boolean(value('methodology').trim()),'wizard.local.methodology'],['wizard.selectedDocumentation',Boolean(packageMode()?$('#package-file').files[0]:$('#description-file').files[0]),'wizard.local.documentation'],['wizard.selectedContent',Boolean(packageMode()?$('#package-file').files[0]:files().length),'wizard.local.content']];
  const container=$('#wizard-quality');container.replaceChildren(uiNode('p','wizard.qualityTotal',{complete:checks.filter(check=>check[1]).length,total:checks.length+metadataProfileWidget.missing().length}));const list=document.createElement('ul');
  for(const [key,complete,why]of checks){const entry=document.createElement('li');entry.append(uiNode('span',complete?'wizard.selectionPresent':'wizard.selectionMissing'),document.createTextNode(': '),uiNode('span',key),document.createTextNode('. '),uiNode('span',why));list.append(entry);}
  for(const item of metadataProfileWidget.missing()){const entry=document.createElement('li');entry.append(uiNode('span','wizard.selectionMissing'),document.createTextNode(': '),uiNode('span','quality.profile',{field:item.label}),document.createTextNode('. '),uiNode('span','wizard.profileRequirement'));list.append(entry);}container.append(list);
  if(needsPostUploadPreview())container.append(uiNode('p','wizard.zipPreflightWarning'));
}
function needsPostUploadPreview() {
  return packageMode() || $('#description-file').files[0]?.name.toLowerCase().endsWith('.zip') || files().some(file => file.name.toLowerCase().endsWith('.zip'));
}
function updateSubmissionButton() {
  uiI18n.set($('#save-submit'),needsPostUploadPreview()?'wizard.saveReview':'wizard.saveSubmit');
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
      .catch(error=>reportFailure('wizard.previewFailed',error));
  }
  if(step===8){$('#save-draft').disabled=busy;$('#save-submit').disabled=busy||!previewReady;}
  document.querySelector('.wizard-progress').scrollIntoView({ behavior: 'instant', block: 'start' });
  const heading=document.querySelector(`.wizard-panel[data-step="${step}"] h2`);heading.tabIndex=-1;heading.focus({preventScroll:true});
  scheduleAutosave();
}
function invalidKey(key,input,params={}) {
  reportKey(key,'error',params);
  if (input) input.focus();
  return false;
}
function validateStep(index) {
  if(index===8&&!field('reviewConfirmed').checked)return invalidKey('wizard.confirmRequired',field('reviewConfirmed'));

  if(index===5&&!validRelations())return false;
  field('temporalEnd').setCustomValidity(value('temporalStart') && value('temporalEnd') && value('temporalStart')>value('temporalEnd')?uiI18n.t('wizard.invalidCoverage'):'');
  const panel = document.querySelector(`.wizard-panel[data-step="${index}"]`);
  for (const input of panel.querySelectorAll('input:not([type=hidden]),textarea,select')) {
    if (!input.checkValidity()) { input.reportValidity(); return invalidKey('wizard.requiredFields', input); }
  }
  if (index === 0) {
    if (!/^\d{4}$/.test(value('year'))) return invalidKey('wizard.invalidYear', field('year'));
  }
  if (index === 1) {
    if (creators().some(author => !author.givenName)) return invalidKey('wizard.authorRequired', $('.creator-given'));
    if (value('orcid') && !/^(?:https:\/\/orcid\.org\/)?\d{4}-\d{4}-\d{4}-[\dX]{4}$/.test(value('orcid')))
      return invalidKey('wizard.invalidOrcid', field('orcid'));
    if (value('ror') && !/^(?:https:\/\/ror\.org\/)?0[0-9a-hjkmnp-tv-z]{6}[0-9]{2}$/.test(value('ror')))
      return invalidKey('wizard.invalidRor', field('ror'));
  }
  if(index===5){
    for (const row of document.querySelectorAll('.funding-row')) {
      const funderRor = row.querySelector('[name="funding-funderRor"]');
      if (funderRor.value.trim() && !/^(?:https:\/\/ror\.org\/)?0[0-9a-hjkmnp-tv-z]{6}[0-9]{2}$/.test(funderRor.value.trim()))
        return invalidKey('wizard.invalidFunderRor', funderRor);
    }
  }
  if (index === 3) {
    if (packageMode()) {
      const archive = $('#package-file').files[0];
      if (!archive || !archive.size) return invalidKey('wizard.selectPackage', $('#package-file'));
      if (!archive.name.toLowerCase().endsWith('.zip')) return invalidKey('wizard.packageMustZip', $('#package-file'));
      return true;
    }
    const description = $('#description-file').files[0];
    if (!description || !description.size) return invalidKey('wizard.selectDescription', $('#description-file'));
    if (description.name !== 'description.md' && !description.name.toLowerCase().endsWith('.zip'))
      return invalidKey('wizard.descriptionFilename', $('#description-file'));
    if (!files().length) return invalidKey('wizard.selectData', $('#dataset-files'));
    const policy = policies[value('type')] || policies.OTHER;
    const paths = new Set();
    for (const file of files()) {
      if (!file.size) return invalidKey('wizard.emptyFile',$('#dataset-files'),{name:file.name});
      const path = file.webkitRelativePath || file.name;
      if (paths.has(path)) return invalidKey('wizard.duplicateFile',$('#dataset-files'),{path});
      paths.add(path);
      const extension = file.name.split('.').pop().toLowerCase();
      if (policy.extensions && extension !== 'zip' && !policy.extensions.includes(extension))
        return invalidKey('wizard.fileTypeRejected',$('#dataset-files'),{type:value('type'),name:file.name});
    }
  }
  if(index===4){
    if(['PERSONAL','CONFIDENTIAL'].includes(value('privacyClassification'))&&value('accessLevel')!=='RESTRICTED')return invalidKey('wizard.sensitiveRestricted',field('accessLevel'));
    if(value('accessLevel')==='EMBARGOED'&&(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(value('embargoUntil'))||Number.isNaN(Date.parse(value('embargoUntil')))))return invalidKey('wizard.invalidEmbargo',field('embargoUntil'));
  }
  if(index===6){const missing=metadataProfileWidget.missing();if(missing.length){const input=field(missing[0].code);if(!input)return invalidKey('wizard.unknownProfileRules');showStep(Number(input.closest('.wizard-panel').dataset.step));return invalidKey('wizard.profileRequired',input,{field:missing[0].label});}}
  if(index===7&&!previewReady)return invalidKey('wizard.waitPreview');
  return true;
}
function uiNode(tag,key,params={},className){const element=document.createElement(tag);uiI18n.set(element,key,params);if(className)element.className=className;return element;}
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
let previewRenderId=0;
async function renderPreview() {
  const renderId=++previewRenderId;
  const target=$('#preview-content');target.replaceChildren();
  const identity=document.createElement('section');identity.className='wizard-preview-section';
  identity.append(node('h3',value('title')),node('p',`${creators().map(author=>`${author.givenName} ${author.familyName}`.trim()).join(', ')} · ${value('publisher')} · ${value('year')}`),uiNode('p','wizard.previewType',{type:value('type'),version:value('versionLabel'),license:value('licenseId')}),uiNode('p','wizard.previewInstitution',{institution:value('institution'),access:value('accessLevel')}),uiNode('p','wizard.previewMethodology',{text:value('methodology')}));
  const profile=metadataProfileWidget.snapshot();if(profile)identity.append(uiNode('p','wizard.previewProfile',{name:metadataProfileWidget.title(profile),revision:profile.revision??uiI18n.t('profile.inherited'),fields:metadataProfileWidget.fields(profile).map(metadataProfileWidget.fieldLabel).join(', ')||uiI18n.t('wizard.baseRules')}));
  if(value('keywords'))identity.append(uiNode('p','wizard.previewKeywords',{text:value('keywords')}));
  if(value('orcid'))identity.append(node('p',`ORCID: ${value('orcid')}`));if(value('ror'))identity.append(node('p',`ROR: ${value('ror')}`));
  for(const item of fundingEntries())identity.append(uiNode('p','wizard.previewFunding',{text:`${item.funderName}${item.awardTitle?` · ${item.awardTitle}`:''}${item.awardNumber?` (${item.awardNumber})`:''}`}));
  if(basedOnId)identity.append(uiNode('p','wizard.newVersionOf',{id:basedOnId}));if(value('privacyClassification'))identity.append(uiNode('p','wizard.previewPrivacy',{classification:value('privacyClassification')}));
  for(const [name,key]of [['productionDescription','wizard.previewProduction'],['processingDescription','wizard.previewProcessing'],['processingTools','wizard.previewTools'],['summary','wizard.previewSummary'],['geographicCoverage','wizard.previewGeo']])if(value(name))identity.append(uiNode('p',key,{text:value(name)}));
  if(value('temporalStart')||value('temporalEnd'))identity.append(uiNode('p','wizard.previewTemporal',{start:value('temporalStart')||uiI18n.t('wizard.noStart'),end:value('temporalEnd')||uiI18n.t('wizard.noEnd')}));
  for(const [lang,item]of Object.entries(translationEditor.get()))identity.append(node('p',`${lang}: ${item.title||''} · ${item.summary||''}`));target.append(identity);
  const citation=document.createElement('section');citation.className='wizard-preview-section';citation.append(uiNode('h3','wizard.draftCitationHeading'));
  const authors=creators().map(author=>[author.familyName,author.givenName].filter(Boolean).join(', ')).join('; ')||uiI18n.t('wizard.pendingAuthors');
  citation.append(uiNode('p','wizard.draftCitation',{authors,year:value('year')||uiI18n.t('wizard.noDate'),title:value('title'),version:value('versionLabel')||uiI18n.t('wizard.pendingVersion'),type:value('type'),publisher:value('publisher')||uiI18n.t('wizard.pendingPublisher')}),uiNode('p','wizard.draftCitationNote',{},'muted'));target.append(citation);
  if(relationEntries().length){const section=node('section','','wizard-preview-section');section.append(uiNode('h3','wizard.relatedResources'));for(const item of relationEntries())section.append(node('p',`${item.kind} · ${item.relationType}: ${item.title||item.identifier} (${item.identifier})`));target.append(section);}
  const description=document.createElement('section');description.className='wizard-preview-section';description.append(uiNode('h3','wizard.description'));
  if(packageMode()){const archive=$('#package-file').files[0];description.append(uiNode('p','wizard.previewPackage',{name:archive.name,type:value('type')}));target.append(description);const list=node('section','','wizard-preview-section');list.append(uiNode('h3','wizard.packageSelected'),node('p',`${archive.name} · ${archive.size.toLocaleString(uiI18n.locale)} bytes`));target.append(list);return;}
  target.append(description); // Mount owned labels before awaiting a local file read.
  const descriptionFile=$('#description-file').files[0];
  if(descriptionFile.name==='description.md'){if(descriptionFile.size>1024*1024)description.append(uiNode('p','wizard.markdownLarge'));else{const markdown=await descriptionFile.text();if(renderId!==previewRenderId)return;const body=node('div','','wizard-markdown');previewMarkdown(markdown,body);description.append(body);}}
  else description.append(uiNode('p','wizard.previewDescriptionZip',{name:descriptionFile.name}));target.append(description);
  const list=node('section','','wizard-preview-section');list.append(uiNode('h3','wizard.selectedFiles'));const ul=document.createElement('ul');for(const file of files()){const item=node('li',`${file.webkitRelativePath||file.name} · ${file.size.toLocaleString(uiI18n.locale)} bytes`);if(file.name.toLowerCase().endsWith('.zip'))item.append(document.createTextNode(' · '),uiNode('span','wizard.zipExtractPending'));ul.append(item);}list.append(ul);target.append(list);
}
async function api(path, options = {}) {
  const response = await fetch(path, { ...options, headers: auth.headers({ Accept: 'application/json', ...options.headers }) });
  const text = await response.text();
  let body; try { body = text ? JSON.parse(text) : null; } catch { body = text; }
  if(!response.ok){const detail=typeof body==='string'?body:body?.detail||body?.message;if(detail)throw new Error(detail);throw uiI18n.error('users.failed');}
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
  if (!basedOnVerified) { reportKey('wizard.ownerPreviousRequired', 'error'); return; }
  if(submit&&!validateStep(8))return;
  for (const index of [0,1,2,3,4,5,6]) {
    if (!validateStep(index)) { showStep(index); validateStep(index); return; }
  }
  busy = true; $('#wizard-back').disabled = true; $('#save-draft').disabled = true; $('#save-submit').disabled = true;
  const payload = { titles: [{ value: value('title') }], publisher: value('publisher'), publicationYear: value('year'), resourceType: { value: value('type'), typeGeneral: value('type') }, creators: creators() };
  try {
    reportKey('wizard.creatingDraft');
    const created = await api('/api/v1/dataresources/', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
    createdId = created.id;
    clearTimeout(autosaveTimer);
    try { localStorage.removeItem(autosaveKey); } catch { /* Storage may be disabled. */ }
    let inheritedConceptualDoi = null;
    if (basedOnId) {
      reportKey('wizard.linkingVersion');
      const derived = await api(`/api/v1/scientific/${encodeURIComponent(createdId)}/derive-from/${encodeURIComponent(basedOnId)}`, { method: 'POST' });
      inheritedConceptualDoi = derived.conceptualDoi;
    }
    reportKey('wizard.savingRecord');
    const savedScience=await api(`/api/v1/scientific/${encodeURIComponent(createdId)}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...sciencePayload(), conceptualDoi: inheritedConceptualDoi }) });
    await metadataProfileWidget.save(createdId,savedScience.revision);
    await scientificPrivacy.saveWizard(createdId);
    if(relationEntries().length)await api(`/api/v1/scientific/${encodeURIComponent(createdId)}/relations`,{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify(relationEntries())});
    if (fundingEntries().length) {
      reportKey('wizard.savingFunding');
      await api(`/api/v1/scientific/${encodeURIComponent(createdId)}/funding`, { method: 'PUT',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(fundingEntries()) });
    }
    if (packageMode()) {
      const archive = $('#package-file').files[0];
      reportKey('wizard.uploadPackage','',{name:archive.name});
      await upload(`/api/v1/dataresources/${encodeURIComponent(createdId)}/attachments?package=true&path=${encodeURIComponent(archive.name)}`, archive);
    } else {
      reportKey('wizard.uploadDescription');
      await upload(`/api/v1/dataresources/${encodeURIComponent(createdId)}/description`, $('#description-file').files[0]);
      for (const [index, file] of files().entries()) {
        reportKey('wizard.uploadFile','',{index:index+1,total:files().length,name:file.name});
        await upload(`/api/v1/dataresources/${encodeURIComponent(createdId)}/attachments?path=${encodeURIComponent(file.webkitRelativePath || file.name)}`, file);
      }
    }
    if (submit && !reviewAfterUpload) { reportKey('wizard.submitting'); await api(`/api/v1/scientific/${encodeURIComponent(createdId)}/submit`, { method: 'POST' }); }
    toast.successKey(reviewAfterUpload?'wizard.savedReview':submit?'wizard.submitted':'wizard.saved');
    busy = false;
    location.assign(`resource.html?id=${encodeURIComponent(createdId)}`);
  } catch (error) {
    reportFailure(createdId?'wizard.partialFailure':null,error,{id:createdId});if(createdId)errorBox.append(document.createTextNode(' '),uiNode('span','wizard.correctDraft'));
    toast.errorObject(error);
    if (createdId) {
      const link = document.createElement('a'); link.href = `resource.html?id=${encodeURIComponent(createdId)}`; uiI18n.set(link,'wizard.openDraft');
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
  uiI18n.set($('#autosave-status'),'wizard.discarded');
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
    throw uiI18n.error('wizard.deriveOnlyOwned');
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
}).catch(error=>reportFailure('wizard.previousFailed',error));

window.addEventListener('ui-locale-changed',()=>{applyPolicy();if(step===6)renderLocalQuality();if((step===7&&previewReady||step===8)&&!busy&&!createdId)renderPreview().catch(error=>reportFailure('wizard.previewFailed',error));field('temporalEnd').setCustomValidity(value('temporalStart')&&value('temporalEnd')&&value('temporalStart')>value('temporalEnd')?uiI18n.t('wizard.invalidCoverage'):'');});
