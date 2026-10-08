/* Public immutable-version lineage. No editorial audit notes or account identities. */
(() => {
  const queryId=new URLSearchParams(location.search).get('id');
  const resourceId=queryId||decodeURIComponent(location.pathname.match(/^\/datasets\/([^/]+)$/)?.[1]||'');
  const status=document.querySelector('#versions-status'),list=document.querySelector('#versions-list'),pagination=document.querySelector('#versions-pagination');
  let page=0,request=0;
  function text(tag,value){const n=document.createElement(tag);n.textContent=value;return n;}
  async function load(){
    const current=++request;status.textContent='Consultando versiones…';
    try{
      const response=await fetch(`/api/v1/public/resources/${encodeURIComponent(resourceId)}/versions?page=${page}&size=20`);if(!response.ok)throw Error('No se pudieron consultar las versiones públicas.');
      const body=await response.json();if(current!==request)return;if(!Array.isArray(body.items))throw Error('No se pudieron consultar las versiones públicas.');
      list.replaceChildren();pagination.replaceChildren();
      if(body.newerPublicationAvailable&&body.latestPublishedId){const links=document.querySelector('#version-links');links.querySelectorAll('.legacy-version-warning,#family-version-warning').forEach(n=>n.remove());const warning=text('p','Hay una versión publicada más reciente en esta familia. ');warning.id='family-version-warning';warning.className='version-warning';const newer=text('a','Ver publicación más reciente');newer.href='/datasets/'+encodeURIComponent(body.latestPublishedId);warning.append(newer);links.append(warning);}
      for(const version of body.items){const item=text('li','');const link=text('a',`Versión ${version.version||'no informada'}${version.current?' · Esta ficha':''}`);link.href='/datasets/'+encodeURIComponent(version.id);if(version.current)link.setAttribute('aria-current','page');item.append(link,text('p',`${version.status==='WITHDRAWN'?'Retirada (ficha permanente)':'Publicada'} · ${version.publishedAt||'Fecha no informada'} · DOI: ${version.doi||'No informado'}`));list.append(item);}
      status.textContent=body.total?`${body.total} versiones públicas · Página ${body.page+1} de ${body.pages}`:'No hay versiones públicas disponibles.';
      if(body.pages>1){const previous=text('button','Anterior');previous.className='secondary';previous.disabled=page===0;previous.onclick=()=>{page--;load();};const next=text('button','Siguiente');next.className='secondary';next.disabled=page+1>=body.pages;next.onclick=()=>{page++;load();};pagination.append(previous,next);}
    }catch(error){if(current!==request)return;status.textContent='No se pudieron consultar las versiones públicas. Puede reintentar sin perder la ficha.';list.replaceChildren();pagination.replaceChildren();const retry=text('button','Reintentar versiones');retry.className='secondary';retry.onclick=load;pagination.append(retry);}
  }
  if(resourceId)load();
})();
