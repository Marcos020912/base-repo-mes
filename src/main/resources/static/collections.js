const collectionParams = new URLSearchParams(location.search);
const collectionId = collectionParams.get('id');
let collectionPage = Math.max(0,parseInt(collectionParams.get('page')||'0',10)||0);
let currentCollection = null, editingCollection = false, confirmOperation = null, collectionRequest = 0;
const collectionManager = ['CURATOR','ADMINISTRATOR'].includes(auth.user()?.role);
const manageMode = document.querySelector('#manage-mode');
manageMode.checked = collectionManager && collectionParams.get('manage')==='true';
const kindFilter = document.querySelector('#kind-filter');
kindFilter.value = collectionParams.get('kind') || '';
const collectionStatus = document.querySelector('#collection-status');
const collectionResults = document.querySelector('#collection-results');
function collectionNode(tag,text,className) {const el=document.createElement(tag);el.textContent=text;if(className)el.className=className;return el;}
function collectionLabel(tag,key,params={},className){const el=collectionNode(tag,'',className);uiI18n.set(el,key,params);return el;}
function collectionUrl(extra={}) {
  const query=new URLSearchParams();
  if(collectionId)query.set('id',collectionId);
  if(manageMode.checked)query.set('manage','true');
  if(kindFilter.value&&!collectionId)query.set('kind',kindFilter.value);
  query.set('page',String(collectionPage));
  Object.entries(extra).forEach(([key,value])=>query.set(key,value));return query;
}
async function collectionApi(path,options={}) {
  const response=await fetch(path,{...options,headers:manageMode.checked?auth.headers({'Content-Type':'application/json'}):{'Content-Type':'application/json'}});
  if(!response.ok) {const body=await response.json().catch(()=>({}));throw body.detail||body.message?new Error(body.detail||body.message):uiI18n.error(response.status===403?'collections.forbidden':'collections.failure');}
  return response.status===204?null:response.json();
}
function collectionBase() {return manageMode.checked?'/api/v1/collections':'/api/v1/public/collections';}
function collectionConfirm(description,operation) {
  uiI18n.set(document.querySelector('#confirm-description'),description);confirmOperation=operation;
  document.querySelector('#collection-confirm').showModal();
}
async function loadCollections() {
  const request=++collectionRequest;
  history.replaceState(null,'',`${location.pathname}?${collectionUrl()}`);
  document.querySelector('#management').hidden=!collectionManager;
  document.querySelector('#new-collection').hidden=!manageMode.checked||Boolean(collectionId);
  document.querySelector('#collection-actions').hidden=!manageMode.checked||!collectionId;
  document.querySelector('#member-form').hidden=!manageMode.checked||!collectionId;
  document.querySelector('#kind-filter-label').hidden=Boolean(collectionId);
  document.querySelector('#back').hidden=!collectionId;
  document.querySelector('#back').href=`collections.html${manageMode.checked?'?manage=true':''}`;
  document.querySelector('#edit-collection').disabled=true;
  document.querySelector('#delete-collection').disabled=true;
  uiI18n.set(collectionStatus,'collections.loading');collectionResults.replaceChildren();
  const pager=document.querySelector('#collection-pagination');pager.replaceChildren();
  try {
    const search=new URLSearchParams({page:String(collectionPage),size:'20',kind:kindFilter.value});
    const data=await collectionApi(`${collectionBase()}${collectionId?'/'+encodeURIComponent(collectionId):''}?${search}`);
    if(request!==collectionRequest)return;
    currentCollection=data.collection||null;
    document.querySelector('#edit-collection').disabled=!currentCollection;
    document.querySelector('#delete-collection').disabled=!currentCollection;
    if(currentCollection?.title)uiI18n.plain(document.querySelector('#heading'),currentCollection.title);else uiI18n.set(document.querySelector('#heading'),'collections.heading');
    if(currentCollection?.description)uiI18n.plain(document.querySelector('#description'),currentCollection.description);else uiI18n.set(document.querySelector('#description'),'collections.defaultDescription');
    const items=data.items||[];
    uiI18n.set(collectionStatus,collectionId?'collections.datasetsTotal':'collections.collectionsTotal',{total:data.total});
    if(!items.length)collectionResults.append(collectionLabel('p',collectionId?'collections.emptyDatasets':'collections.emptyCollections',{},'panel empty'));
    for(const item of items) {
      const card=collectionNode('article','','resource-card');
      const link=item.title?collectionNode('a',item.title):collectionLabel('a','collections.untitled');
      link.href=collectionId?(manageMode.checked?`resource.html?id=${encodeURIComponent(item.id)}`:`/datasets/${encodeURIComponent(item.id)}`):`collections.html?id=${encodeURIComponent(item.id)}${manageMode.checked?'&manage=true':''}`;
      const heading=collectionNode('h2','');heading.append(link);card.append(heading);
      if(collectionId) {
        card.append(item.authors?.length?collectionNode('p',item.authors.join(', ')):collectionLabel('p','collections.noAuthors'));const info=collectionNode('small','');info.append(item.type?collectionNode('span',item.type):collectionLabel('span','collections.resource'),collectionNode('span',' · '),item.year?collectionNode('span',item.year):collectionLabel('span','collections.noYear'));card.append(info);
        if(manageMode.checked) {
          card.append(collectionLabel('p','collections.status',{status:item.status}));
          const remove=collectionLabel('button','collections.remove',{},'danger');remove.type='button';
          remove.onclick=()=>collectionConfirm('collections.removePolicy',()=>collectionApi(`${collectionBase()}/${encodeURIComponent(collectionId)}/datasets/${encodeURIComponent(item.id)}`,{method:'DELETE'}));card.append(remove);
        }
      } else {
        card.append(item.description?collectionNode('p',item.description):collectionLabel('p','collections.noDescription'));const kind=collectionNode('small','');kind.append(item.kind==='THEMATIC'?collectionLabel('span','collections.thematic'):item.kind==='INSTITUTIONAL'?collectionLabel('span','collections.institutional'):collectionNode('span',item.kind),collectionNode('span',' · '),collectionLabel('span','collections.datasetCount',{total:item.datasets}));card.append(kind);
        if(manageMode.checked)card.append(collectionLabel('small',item.published?'collections.public':'collections.private'));
      }
      collectionResults.append(card);
    }
    for(const [text,next] of [['collections.previous',data.page-1],['collections.next',data.page+1]])if(next>=0&&next<data.pages) {
      const button=collectionLabel('button',text,{},'secondary');button.type='button';button.onclick=()=>{collectionPage=next;loadCollections();};pager.append(button);
    }
  } catch(error) {if(request!==collectionRequest)return;uiI18n.showError(collectionStatus,error);toast.errorObject(error);}
}
function openCollectionForm(edit) {
  editingCollection=edit;const form=document.querySelector('#collection-form');form.reset();
  uiI18n.set(document.querySelector('#collection-dialog-heading'),edit?'collections.edit':'collections.create');
  if(edit&&currentCollection)for(const field of ['title','description','kind'])form.elements[field].value=currentCollection[field]||'';
  form.elements.published.checked=edit&&currentCollection?.published;
  document.querySelector('#collection-dialog').showModal();
}
manageMode.onchange=()=>{collectionPage=0;loadCollections();};
kindFilter.onchange=()=>{collectionPage=0;loadCollections();};
document.querySelector('#new-collection').onclick=()=>openCollectionForm(false);
document.querySelector('#edit-collection').onclick=()=>openCollectionForm(true);
document.querySelector('#delete-collection').onclick=()=>collectionConfirm('collections.deletePolicy',async()=>{
  await collectionApi(`/api/v1/collections/${encodeURIComponent(collectionId)}`,{method:'DELETE'});
  location.assign('collections.html?manage=true');
});
document.querySelectorAll('[data-close]').forEach(button=>button.onclick=()=>document.getElementById(button.dataset.close).close());
document.querySelector('#collection-form').onsubmit=async event=>{
  event.preventDefault();const form=event.currentTarget,button=form.querySelector('[type=submit]');if(button.disabled)return;button.disabled=true;
  const data=new FormData(form);const payload={title:data.get('title'),description:data.get('description'),kind:data.get('kind'),published:form.elements.published.checked};
  if(editingCollection)payload.revision=currentCollection.revision;
  try {await collectionApi(`/api/v1/collections${editingCollection?'/'+encodeURIComponent(collectionId):''}`,{method:editingCollection?'PUT':'POST',body:JSON.stringify(payload)});
    document.querySelector('#collection-dialog').close();toast.successKey('collections.saved');await loadCollections();
  }catch(error){toast.errorObject(error);}finally{button.disabled=false;}
};
document.querySelector('#member-form').onsubmit=async event=>{
  event.preventDefault();const form=event.currentTarget,button=form.querySelector('button');if(button.disabled)return;button.disabled=true;
  const id=form.elements.resourceId.value.trim();
  try {await collectionApi(`/api/v1/collections/${encodeURIComponent(collectionId)}/datasets/${encodeURIComponent(id)}`,{method:'PUT'});form.reset();toast.successKey('collections.added');await loadCollections();}
  catch(error){toast.errorObject(error);}finally{button.disabled=false;}
};
document.querySelector('#confirm-action').onclick=async event=>{
  const button=event.currentTarget;if(button.disabled||!confirmOperation)return;button.disabled=true;
  try {await confirmOperation();document.querySelector('#collection-confirm').close();toast.successKey('collections.completed');await loadCollections();}
  catch(error){toast.errorObject(error);}finally{button.disabled=false;}
};
loadCollections();
