const state = { page: 0, pages: 0 };
const q = document.querySelector('#public-q');
const type = document.querySelector('#public-type');
const discipline = document.querySelector('#public-discipline'); const institution = document.querySelector('#public-institution'); const language = document.querySelector('#public-language'); const format = document.querySelector('#public-format'); const access = document.querySelector('#public-access'); const sort = document.querySelector('#public-sort');
const results = document.querySelector('#public-results');
const pagination = document.querySelector('#public-pagination');
const status = document.querySelector('#public-status');
const facetPanel = document.createElement('aside');
facetPanel.className = 'public-facet-panel';
uiI18n.attribute(facetPanel,'aria-label','catalog.facets');
const facetLayout = document.createElement('div');
facetLayout.className = 'public-facet-layout';
const facetResults = document.createElement('div');
results.before(facetLayout);
facetResults.append(results, pagination);
facetLayout.append(facetPanel, facetResults);
let authorFilter = '', yearFilter = '', licenseFilter = '', mimeFilter = '', funderFilter = '', projectFilter = '', doiFilter = '';
let pendingRequest = 0;
const defaultSort=sort.value;
function restorePublicFilters() {
  const params=new URLSearchParams(location.search);
  q.value=params.get('q')||'';type.value=params.get('type')||'';
  for(const [key,control] of [['discipline',discipline],['institution',institution],['language',language],['format',format],['access',access]])control.value=params.get(key)||'';
  sort.value=params.get('sort')||defaultSort;
  state.page=Math.max(0,Number.parseInt(params.get('page')||'0',10)||0);
  authorFilter=params.get('author')||'';yearFilter=params.get('year')||'';licenseFilter=params.get('license')||'';mimeFilter=params.get('mimeType')||'';
  funderFilter=params.get('funder')||'';projectFilter=params.get('project')||'';
  doiFilter=params.get('hasDoi')==='true'?'true':params.get('withoutDoi')==='true'?'false':'';
}
restorePublicFilters();
function text(tag, value, className) { const node = document.createElement(tag); node.textContent = value; if (className) node.className = className; return node; }
function uiText(tag,key,className,params={}){const node=document.createElement(tag);if(className)node.className=className;uiI18n.set(node,key,params);return node;}
const facetNames = {type:'catalog.type',author:'catalog.authorship',access:'catalog.access',year:'catalog.year',license:'catalog.license',discipline:'catalog.discipline',institution:'catalog.institution',language:'catalog.language',mimeType:'catalog.format',funder:'catalog.funder',project:'catalog.project',hasDoi:null};
const typeLabels={DATASET:'catalog.dataset',IMAGE:'catalog.image',TEXT:'catalog.text',AUDIOVISUAL:'catalog.video',OTHER:'catalog.other'};
const facetLabels = {OPEN:'catalog.open',RESTRICTED:'catalog.restricted',EMBARGOED:'catalog.embargo',true:'catalog.withDoi',false:'catalog.withoutDoi'};
const mimeLabels = {'text/csv':'CSV','application/pdf':'PDF','application/json':'JSON','text/plain':'TXT','image/png':'PNG','image/jpeg':'JPEG','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':'XLSX','application/vnd.ms-excel':'XLS','application/vnd.oasis.opendocument.spreadsheet':'ODS'};
function selectedFacet(name) {
  return ({type:type.value,author:authorFilter,access:access.value,year:yearFilter,license:licenseFilter,
    discipline:discipline.value,institution:institution.value,language:language.value,mimeType:mimeFilter,
    funder:funderFilter,project:projectFilter,
    hasDoi:doiFilter})[name];
}
function chooseFacet(name, value) {
  const next = selectedFacet(name) === value ? '' : value;
  if (name === 'type') type.value = next;
  else if (name === 'author') authorFilter = next;
  else if (name === 'access') access.value = next;
  else if (name === 'year') yearFilter = next;
  else if (name === 'license') licenseFilter = next;
  else if (name === 'discipline') discipline.value = next;
  else if (name === 'institution') institution.value = next;
  else if (name === 'language') language.value = next;
  else if (name === 'mimeType') mimeFilter = next;
  else if (name === 'funder') funderFilter = next;
  else if (name === 'project') projectFilter = next;
  else if (name === 'hasDoi') doiFilter = next;
  state.page = 0; load();
}
function renderFacets(data) {
  facetPanel.replaceChildren(uiText('h2','catalog.filterResults'));
  for (const [name, label] of Object.entries(facetNames)) {
    const values = data[name] || []; if (!values.length) continue;
    const group = document.createElement('section'); group.className = 'public-facet-group';
    group.append(label?uiText('h3',label):text('h3','DOI'));
    for (const option of values) {
      const button=document.createElement('button');button.className='public-facet-option';
      const key=name==='type'?typeLabels[option.value]:(name==='access'||name==='hasDoi')?facetLabels[option.value]:null;
      button.append(key?uiText('span',key):text('span',name==='mimeType'?(mimeLabels[option.value]||option.value):option.value),text('span',` (${option.count})`));
      button.type = 'button'; button.setAttribute('aria-pressed', selectedFacet(name) === option.value ? 'true' : 'false');
      button.addEventListener('click', () => chooseFacet(name, option.value)); group.append(button);
    }
    facetPanel.append(group);
  }
}
async function load({historyMode='push'}={}) {
  const requestId = ++pendingRequest;
  const search = new URLSearchParams({ q: q.value.trim(), author:authorFilter, type: type.value, year:yearFilter, license:licenseFilter,
    discipline: discipline.value.trim(), institution: institution.value.trim(), language: language.value.trim(),
    format: format.value.trim(), mimeType:mimeFilter, funder:funderFilter, project:projectFilter,
    access: access.value, hasDoi:String(doiFilter === 'true'),
    withoutDoi:String(doiFilter === 'false'), sort: sort.value,
    page: String(state.page), size: '20' });
  const target=`${location.pathname}?${search}${location.hash}`;
  if(historyMode==='replace')history.replaceState(null,'',target);
  else if(historyMode==='push'&&target!==`${location.pathname}${location.search}${location.hash}`)history.pushState(null,'',target);
  uiI18n.set(status,'catalog.searching');
  try {
    const response = await fetch(`/api/v1/public/catalog?${search}`);
    if (response.status === 403) throw uiI18n.error('catalog.forbidden');
    if (!response.ok) throw uiI18n.error('catalog.unavailable');
    const data = await response.json(); if (requestId !== pendingRequest) return;
    if(!Array.isArray(data.items)||!Number.isInteger(data.page)||!Number.isInteger(data.pages)||!Number.isFinite(data.total))throw uiI18n.error('catalog.unavailable');
    state.page = data.page; state.pages = data.pages;
    results.replaceChildren(); pagination.replaceChildren();
    if (!data.items.length) {
      const filtered = [q.value, authorFilter, type.value, yearFilter, licenseFilter, discipline.value, institution.value,
        language.value, format.value, mimeFilter, funderFilter, projectFilter, access.value].some(Boolean) || doiFilter;
      const empty = document.createElement('div'); empty.className = 'panel empty';
      empty.append(uiText('p',filtered?'catalog.emptyFiltered':'catalog.publicEmpty'));
      if (filtered) { const clear = uiText('button','catalog.clear','secondary'); clear.type = 'button';
        clear.onclick = () => { q.value=''; authorFilter=''; type.value=''; discipline.value=''; institution.value=''; language.value=''; format.value=''; mimeFilter=''; funderFilter=''; projectFilter=''; access.value=''; yearFilter=''; licenseFilter=''; doiFilter=''; state.page=0; load(); };
        empty.append(clear); }
      results.append(empty);
    }
    for (const item of data.items) {
      const link = document.createElement('a'); link.className = 'resource-card'; link.href = `/datasets/${encodeURIComponent(item.id)}`;
      link.append(typeLabels[item.type]?uiText('span',typeLabels[item.type],'resource-type'):item.type?text('span',item.type,'resource-type'):uiText('span','catalog.resource','resource-type'),item.title?text('h2',item.title):uiText('h2','catalog.untitled'),item.authors?.length?text('p',item.authors.join(', '),'card-author'):uiText('p','catalog.noAuthor','card-author'));
      const publication=document.createElement('p');publication.append(item.publisher?text('span',item.publisher):uiText('span','catalog.noInstitution'),document.createTextNode(' · '),item.year?text('span',item.year):uiText('span','catalog.noYear'));link.append(publication);
      link.append(/^10\.\d{4,9}\/\S+$/.test(item.identifier||'')?text('small',`DOI: ${item.identifier}`):uiText('small','catalog.doiRecord'),uiText('small',({OPEN:'catalog.openAccess',RESTRICTED:'catalog.restrictedAccess',EMBARGOED:'catalog.underEmbargo'})[item.accessLevel]||'catalog.noAccess'));
      results.append(link);
    }
    if (state.pages > 1) {
      const prev = uiText('button','catalog.previous','secondary'); prev.disabled = state.page === 0; prev.onclick = () => { state.page--; load(); };
      const next = uiText('button','catalog.next','secondary'); next.disabled = state.page + 1 >= state.pages; next.onclick = () => { state.page++; load(); };
      pagination.append(prev, uiText('span','catalog.page',null,{page:state.page+1,pages:state.pages}), next);
    }
    uiI18n.set(status,'catalog.publicTotal',{total:data.total});
    try {
      const facetSearch = new URLSearchParams(search); facetSearch.delete('sort'); facetSearch.delete('page'); facetSearch.delete('size');
      const facetResponse = await fetch(`/api/v1/public/catalog/facets?${facetSearch}`);
      if (facetResponse.ok) { const facets = await facetResponse.json(); if (requestId === pendingRequest) renderFacets(facets); }
      else if (requestId === pendingRequest) facetPanel.replaceChildren(uiText('p','catalog.facetsFailed'));
    } catch { if (requestId === pendingRequest) facetPanel.replaceChildren(uiText('p','catalog.facetsFailed')); }
  } catch(error){if(requestId!==pendingRequest)return;const forbidden=error.i18nKey==='catalog.forbidden';uiI18n.set(status,forbidden?'catalog.forbidden':'catalog.unavailable');results.replaceChildren();pagination.replaceChildren();facetPanel.replaceChildren();const panel=text('div','','panel empty');const action=uiText(forbidden?'a':'button',forbidden?'catalog.helpAction':'catalog.retry','secondary');if(forbidden)action.href='mailto:soporte@mes.gob.cu';else{action.type='button';action.onclick=()=>load();}panel.append(action);results.append(panel); }
}
let timer; q.addEventListener('input', () => { state.page = 0; clearTimeout(timer); timer = setTimeout(load, 300); });
for(const control of [discipline,institution,language,format]) control.addEventListener('input', () => { state.page=0; clearTimeout(timer); timer=setTimeout(load,300); });
for(const control of [type,access,sort]) control.addEventListener('change', () => { state.page=0; load(); });
window.addEventListener('popstate',()=>{clearTimeout(timer);restorePublicFilters();load({historyMode:'none'});});
load({historyMode:'replace'});

let metricsDate=null;
function renderMetricsDate(){if(metricsDate)uiI18n.set(document.querySelector('#metrics-status'),'catalog.updated',{date:metricsDate.toLocaleString(uiI18n.locale)});}
window.addEventListener('ui-locale-changed',renderMetricsDate);
async function loadPublicMetrics() {
  const notice=document.querySelector('#metrics-status');
  try {
    const response=await fetch('/api/v1/public/metrics');
    if(!response.ok)throw uiI18n.error('catalog.metricsUnavailable');
    const data=await response.json();
    const list=document.querySelector('#public-metrics');list.replaceChildren();
    for(const [key,label] of [['publishedVersions','catalog.publishedVersions'],['openPolicyVersions','catalog.openPolicyVersions'],['publishedCollections','catalog.publishedCollections']]) {
      if(!Number.isSafeInteger(data[key])||data[key]<0)throw uiI18n.error('catalog.metricsUnavailable');
      list.append(uiText('dt',label),text('dd',String(data[key])));
    }
    const definitions=document.querySelector('#metrics-definitions');definitions.replaceChildren();
    Object.entries(data.definitions||{}).forEach(([key,value])=>definitions.append(['publishedVersions','openPolicyVersions','publishedCollections'].includes(key)?uiText('li',`catalog.definition.${key}`):text('li',value)));
    metricsDate=new Date(data.generatedAt||data.measuredAt);if(!Number.isFinite(metricsDate.getTime()))throw uiI18n.error('catalog.metricsUnavailable');renderMetricsDate();
  } catch(error) {metricsDate=null;uiI18n.set(notice,'catalog.metricsUnavailable');document.querySelector('#public-metrics').replaceChildren();}
}
loadPublicMetrics();
