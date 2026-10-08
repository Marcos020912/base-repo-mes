(() => {
  auth.requireLogin();const user=auth.user();if(!['CURATOR','ADMINISTRATOR'].includes(user?.role)){location.replace('index.html');return;}
  document.querySelector('#current-user').textContent=user.username;if(user.role==='ADMINISTRATOR')document.querySelector('#users-nav').hidden=false;
  document.querySelector('[data-logout]').onclick=auth.logout;
  const status=document.querySelector('#operations-status'),exportButton=document.querySelector('#export-operations');let report=null,pending=0;
  function text(tag,value){const n=document.createElement(tag);n.textContent=value;return n;}
  function bytes(value){return Number.isFinite(value)?`${new Intl.NumberFormat('es').format(value)} bytes`:'No disponible';}
  async function get(path){const response=await fetch(path,{headers:auth.headers({Accept:'application/json'}),cache:'no-store'});const body=await response.json().catch(()=>({}));if(!response.ok)throw Error(body.detail||'No se pudo consultar el estado operativo.');return body;}
  async function load(){
    const request=++pending;status.textContent='Consultando estado…';exportButton.disabled=true;report=null;
    try{
      const [storage,metrics,audits]=await Promise.all([get('/api/v1/scientific/preservation/storage'),get('/api/v1/scientific/preservation/metrics'),get('/api/v1/scientific/preservation/audits')]);if(request!==pending)return;
      const measuredAt=new Date().toISOString();report={schema:'reduniv-operational-report/1',measuredAt,storage,metrics,audits};
      const capacity=document.querySelector('#storage-summary');capacity.replaceChildren(text('p',storage.status==='AVAILABLE'?'Volumen consultado correctamente':'No se pudo medir el almacenamiento local'),text('p',storage.scope),text('p',`Capacidad: ${bytes(storage.totalBytes)} · Disponible para la aplicación: ${bytes(storage.usableBytes)} · Sin asignar: ${bytes(storage.unallocatedBytes)}`),text('p',`Medido: ${storage.measuredAt}`));
      const integrity=document.querySelector('#integrity-summary');integrity.replaceChildren();
      for(const [key,label] of Object.entries({published:'Versiones publicadas registradas',inReview:'Depósitos en revisión',files:'Registros técnicos de archivos',filesChecked:'Estados independientes de verificación',mismatched:'Archivos con checksum diferente',missing:'Archivos ausentes'}))integrity.append(text('p',`${label}: ${metrics[key]}`));
      const history=document.querySelector('#operations-audits');history.replaceChildren();
      if(!audits.length)history.append(text('p','Todavía no hay auditorías registradas.'));
      for(const audit of audits){const item=text('article','');item.className='panel';item.append(text('h3',`${audit.status} · ${audit.startedAt}`),text('p',`Finalización: ${audit.completedAt||'No finalizada'} · Comprobados: ${audit.checked} · Coinciden: ${audit.matched} · Alterados: ${audit.mismatched} · Ausentes: ${audit.missing} · Sin huella: ${audit.noBaseline} · No compatibles: ${audit.unsupported} · Errores: ${audit.errors}`));history.append(item);}
      status.textContent=`Informe consultado: ${measuredAt}`;exportButton.disabled=false;
    }catch(error){if(request!==pending)return;status.textContent=error.message+' Reintente la consulta.';for(const id of ['storage-summary','integrity-summary','operations-audits'])document.querySelector('#'+id).replaceChildren(text('p','Datos no disponibles; no se muestran cifras anteriores como actuales.'));}
  }
  document.querySelector('#refresh-operations').onclick=load;
  exportButton.onclick=()=>{if(!report)return;const url=URL.createObjectURL(new Blob([JSON.stringify(report,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='reduniv-operaciones.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
  load();
})();
