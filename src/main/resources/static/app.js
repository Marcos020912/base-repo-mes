const $ = selector => document.querySelector(selector);
auth.requireLogin();
const user = auth.user();
$('#current-user').textContent = user?.username || '';
if (user?.role === 'ADMINISTRATOR') $('#users-nav').hidden = false;
document.querySelectorAll('[data-logout]').forEach(button => button.addEventListener('click', auth.logout));
if (new URLSearchParams(location.search).get('new') === '1') location.replace(`create.html${location.search}`);
const state = { resources: [], page: 0, pages: 0, total: 0 };
const doiOf = item => /^10\.\d{4,9}\/\S+$/.test(item.identifier || '') ? item.identifier : '';
function uiNode(tag,key,params={},className='') {const node=document.createElement(tag);if(className)node.className=className;uiI18n.set(node,key,params);return node;}
function rawNode(tag,value,className='') {const node=document.createElement(tag);node.textContent=value;if(className)node.className=className;return node;}
function filterLabel(key,control) {const label=document.createElement('label');label.append(uiNode('span',key),control);return label;}
const category = document.createElement('select');category.id='category-filter';
for(const [value,key] of [['','categories'],['DATASET','dataset'],['IMAGE','images'],['TEXT','texts'],['AUDIOVISUAL','videos'],['OTHER','others']]){const option=uiNode('option',`catalog.${key}`);option.value=value;category.append(option);}
const authorFilter=document.createElement('input');authorFilter.id='author-filter';authorFilter.type='search';uiI18n.attribute(authorFilter,'placeholder','catalog.authorPlaceholder');
$('#filter-controls').append(filterLabel('catalog.category',category),filterLabel('catalog.author',authorFilter));
function addAdvanced(name,key,placeholder,type='search',placeholderKey) {const input=document.createElement('input');input.id=name;input.type=type;if(type==='checkbox')input.className='filter-checkbox';else if(placeholderKey)uiI18n.attribute(input,'placeholder',placeholderKey);else input.placeholder=placeholder;$('#advanced-filters').append(filterLabel(key,input));return input;}
addAdvanced('year-filter','catalog.year','2026');addAdvanced('license-filter','catalog.license','CC-BY-4.0');addAdvanced('discipline-filter','catalog.discipline','','search','catalog.biology');addAdvanced('institution-filter','catalog.institution','','search','catalog.university');addAdvanced('language-filter','catalog.language','es');addAdvanced('format-filter','catalog.fileFormat','csv');addAdvanced('doi-filter','catalog.onlyDoi','', 'checkbox');
const accessFilter=document.createElement('select');accessFilter.id='access-filter';for(const [value,key] of [['','all'],['OPEN','open'],['RESTRICTED','restricted'],['EMBARGOED','embargo']]){const option=uiNode('option',`catalog.${key}`);option.value=value;accessFilter.append(option);}$('#advanced-filters').append(filterLabel('catalog.access',accessFilter));
function filters() {return {q:$('#filter').value.trim(),author:authorFilter.value.trim(),type:category.value,year:$('#year-filter').value.trim(),license:$('#license-filter').value.trim(),discipline:$('#discipline-filter').value.trim(),institution:$('#institution-filter').value.trim(),language:$('#language-filter').value.trim(),format:$('#format-filter').value.trim(),access:accessFilter.value,sort:$('#sort-order').value,hasDoi:$('#doi-filter').checked?'true':'false',page:String(state.page),size:$('#page-size').value};}
const filterControls=[['q','filter'],['author','author-filter'],['type','category-filter'],['year','year-filter'],['license','license-filter'],['discipline','discipline-filter'],['institution','institution-filter'],['language','language-filter'],['format','format-filter'],['access','access-filter'],['sort','sort-order'],['size','page-size']];
const filterDefaults=Object.fromEntries(filterControls.map(([key,id])=>[key,$(`#${id}`).value]));
function restoreFilters(){const params=new URLSearchParams(location.search);for(const [key,id]of filterControls)$(`#${id}`).value=params.get(key)??filterDefaults[key];$('#doi-filter').checked=params.get('hasDoi')==='true';state.page=Math.max(0,Number.parseInt(params.get('page')||'0',10)||0);}
restoreFilters();
async function api(path,options={}) {
  let response;try{response=await fetch(path,{...options,headers:auth.headers({Accept:'application/json',...options.headers})});}catch{throw uiI18n.error('catalog.unavailable');}
  const content=await response.text();let body;try{body=content?JSON.parse(content):null;}catch{body=null;}
  if(!response.ok){if(response.status===403)throw uiI18n.error('catalog.forbidden');if(response.status===401)throw uiI18n.error('catalog.expired');if(response.status>=500)throw uiI18n.error('catalog.unavailable');const detail=body?.message||body?.detail;if(detail)throw new Error(detail);throw uiI18n.error('catalog.failed');}
  if(!body||!Array.isArray(body.items))throw uiI18n.error('catalog.invalid');return body;
}
function hasActiveFilters(){const values=filters();return Object.entries(values).some(([key,value])=>!['sort','page','size','hasDoi'].includes(key)&&Boolean(value))||values.hasDoi==='true';}
function render() {
  const list=$('#resource-list');list.replaceChildren();
  for(const item of state.resources){
    const card=document.createElement('a');card.className='resource-card';card.href=`resource.html?id=${encodeURIComponent(item.id)}`;
    const typeKey=({DATASET:'catalog.dataset',IMAGE:'catalog.image',TEXT:'catalog.text',AUDIOVISUAL:'catalog.video',OTHER:'catalog.other'})[item.type||'OTHER'];
    card.append(typeKey?uiNode('span',typeKey,{},'resource-type'):rawNode('span',item.type,'resource-type'),item.title?rawNode('h2',item.title):uiNode('h2','catalog.untitled'),item.authors?.length?rawNode('p',item.authors.join(', '),'card-author'):uiNode('p','catalog.noAuthor',{},'card-author'));
    const publication=document.createElement('p');publication.append(item.publisher?rawNode('span',item.publisher):uiNode('span','catalog.noInstitution'),document.createTextNode(' · '),item.year?rawNode('span',item.year):uiNode('span','catalog.noYear'));card.append(publication);
    const identifier=doiOf(item)?rawNode('small',`DOI: ${doiOf(item)}`):uiNode('small','catalog.noDoi');identifier.title=doiOf(item)||item.id;card.append(identifier);list.append(card);
  }
  if(!state.resources.length){const empty=document.createElement('div');empty.className='empty panel';empty.append(uiNode('p',hasActiveFilters()?'catalog.emptyFiltered':'catalog.empty'));if(hasActiveFilters()){const clear=uiNode('button','catalog.clear',{},'secondary');clear.type='button';clear.onclick=()=>{for(const[key,id]of filterControls)$(`#${id}`).value=filterDefaults[key];$('#doi-filter').checked=false;state.page=0;load();};empty.append(clear);}list.append(empty);}
  let pagination=$('#pagination');if(!pagination){pagination=document.createElement('div');pagination.id='pagination';pagination.className='pagination';list.after(pagination);}pagination.replaceChildren();
  if(state.pages>1){const prev=uiNode('button','catalog.previous',{},'secondary');prev.disabled=state.page===0;prev.onclick=()=>{state.page--;load();};const next=uiNode('button','catalog.next',{},'secondary');next.disabled=state.page+1>=state.pages;next.onclick=()=>{state.page++;load();};pagination.append(prev,uiNode('span','catalog.page',{page:state.page+1,pages:state.pages}),next);}
}
let pendingRequest=0;
async function load({historyMode='push'}={}) {
  const requestId=++pendingRequest;uiI18n.set($('#status-text'),'catalog.searching');const params=new URLSearchParams(filters());const target=`${location.pathname}?${params}${location.hash}`;if(historyMode==='replace')history.replaceState(null,'',target);else if(historyMode==='push'&&target!==`${location.pathname}${location.search}${location.hash}`)history.pushState(null,'',target);
  try{const result=await api(`/api/v1/catalog?${params}`);if(requestId!==pendingRequest)return;state.resources=result.items;state.total=result.total||0;state.pages=result.pages||0;state.page=result.page||0;render();uiI18n.set($('#status-text'),'catalog.total',{total:state.total});}
  catch(error){if(requestId!==pendingRequest)return;const list=$('#resource-list');list.replaceChildren();const empty=document.createElement('div');empty.className='empty panel';const message=document.createElement('p');uiI18n.showError(message,error);empty.append(message);uiI18n.set($('#status-text'),'catalog.failed');$('#pagination')?.replaceChildren();const forbidden=error.i18nKey==='catalog.forbidden',expired=error.i18nKey==='catalog.expired';const action=uiNode(forbidden||expired?'a':'button',forbidden?'catalog.helpAction':expired?'catalog.login':'catalog.retry',{},'secondary');if(forbidden)action.href='mailto:soporte@mes.gob.cu';else if(expired)action.href='login.html';else{action.type='button';action.onclick=()=>load();}empty.append(action);list.append(empty);if(window.toast?.errorObject)toast.errorObject(error);else if(window.toast)toast.error(error.message);}
}
let searchTimer;function scheduleSearch(){state.page=0;clearTimeout(searchTimer);searchTimer=setTimeout(load,300);}
for(const id of ['filter','author-filter','year-filter','license-filter','discipline-filter','institution-filter','language-filter','format-filter'])$(`#${id}`).addEventListener('input',scheduleSearch);
for(const id of ['category-filter','doi-filter','access-filter','sort-order','page-size'])$(`#${id}`).addEventListener('change',()=>{state.page=0;load();});
$('#refresh-list').addEventListener('click',load);
window.addEventListener('popstate',()=>{clearTimeout(searchTimer);restoreFilters();load({historyMode:'none'});});
load({historyMode:'replace'});
