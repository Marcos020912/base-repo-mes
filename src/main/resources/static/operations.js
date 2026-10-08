(() => {
  auth.requireLogin();const user=auth.user();if(!['CURATOR','ADMINISTRATOR'].includes(user?.role)){location.replace('index.html');return;}
  document.querySelector('#current-user').textContent=user.username;if(user.role==='ADMINISTRATOR')document.querySelector('#users-nav').hidden=false;
  document.querySelector('[data-logout]').onclick=auth.logout;
  const status=document.querySelector('#operations-status'),exportButton=document.querySelector('#export-operations');let report=null,pending=0;
  function text(tag,value){const n=document.createElement(tag);n.textContent=value;return n;}
  function ui(tag,key,params={}){const n=document.createElement(tag);uiI18n.set(n,key,params);return n;}
  function bytes(value){return Number.isFinite(value)?text('span',`${new Intl.NumberFormat(uiI18n.locale).format(value)} bytes`):ui('span','operations.notAvailable');}
  function measuredDate(value){const date=new Date(value);return Number.isFinite(date.getTime())?date.toLocaleString(uiI18n.locale):String(value);}
  async function get(path){const response=await fetch(path,{headers:auth.headers({Accept:'application/json'}),cache:'no-store'});const body=await response.json().catch(()=>({}));if(!response.ok){if(body.detail)throw Error(body.detail);throw uiI18n.error('operations.failed');}return body;}
  function renderReport(){
    if(!report)return;const {storage,metrics,audits,measuredAt}=report;
    const capacity=document.querySelector('#storage-summary');capacity.replaceChildren(ui('p',storage.status==='AVAILABLE'?'operations.available':'operations.storageUnavailable'),ui('p','operations.scope'));
    const sizes=document.createElement('p');for(const [key,value]of [['capacity',storage.totalBytes],['usable',storage.usableBytes],['unallocated',storage.unallocatedBytes]]){if(sizes.childNodes.length)sizes.append(document.createTextNode(' · '));sizes.append(ui('span',`operations.${key}`),document.createTextNode(': '),bytes(value));}capacity.append(sizes,ui('p','operations.measured',{date:measuredDate(storage.measuredAt)}));
    const integrity=document.querySelector('#integrity-summary');integrity.replaceChildren();for(const key of ['published','inReview','files','filesChecked','mismatched','missing']){const row=document.createElement('p');row.append(ui('span',`operations.${key}`),document.createTextNode(`: ${metrics[key]}`));integrity.append(row);}
    const history=document.querySelector('#operations-audits');history.replaceChildren();if(!audits.length)history.append(ui('p','operations.noAudits'));
    for(const audit of audits){const item=text('article','');item.className='panel';item.append(text('h3',`${audit.status} · ${measuredDate(audit.startedAt)}`));const detail=document.createElement('p');detail.append(ui('span','operations.completed'),document.createTextNode(': '),audit.completedAt?text('span',measuredDate(audit.completedAt)):ui('span','operations.notCompleted'));for(const [key,value]of [['checked',audit.checked],['matched',audit.matched],['altered',audit.mismatched],['absent',audit.missing],['noBaseline',audit.noBaseline],['unsupported',audit.unsupported],['errors',audit.errors]])detail.append(document.createTextNode(' · '),ui('span',`operations.${key}`),document.createTextNode(`: ${value}`));item.append(detail);history.append(item);}
    uiI18n.set(status,'operations.reportDate',{date:measuredDate(measuredAt)});
  }
  async function load(){const request=++pending;uiI18n.set(status,'operations.loading');exportButton.disabled=true;report=null;
    try{const [storage,metrics,audits]=await Promise.all([get('/api/v1/scientific/preservation/storage'),get('/api/v1/scientific/preservation/metrics'),get('/api/v1/scientific/preservation/audits')]);if(request!==pending)return;if(!storage||!metrics||!Array.isArray(audits))throw uiI18n.error('operations.failed');const measuredAt=new Date().toISOString();report={schema:'reduniv-operational-report/1',measuredAt,storage,metrics,audits};renderReport();exportButton.disabled=false;}
    catch(error){if(request!==pending)return;report=null;uiI18n.plain(status,'');const message=document.createElement('span');uiI18n.showError(message,error);status.append(message,document.createTextNode(' '),ui('span','operations.retry'));for(const id of ['storage-summary','integrity-summary','operations-audits'])document.querySelector('#'+id).replaceChildren(ui('p','operations.noStale'));}
  }
  window.addEventListener('ui-locale-changed',renderReport);
  document.querySelector('#refresh-operations').onclick=load;
  exportButton.onclick=()=>{if(!report)return;const url=URL.createObjectURL(new Blob([JSON.stringify(report,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='reduniv-operaciones.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};load();
})();
