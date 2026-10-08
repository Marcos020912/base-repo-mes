/* Author-only task projection; errors do not masquerade as an empty queue. */
(() => {
  const section=document.querySelector('#deposit-tasks'),list=document.querySelector('#task-list'),status=document.querySelector('#task-status');
  let page=0,pages=0,request=0;
  function text(tag,value){const n=document.createElement(tag);n.textContent=value;return n;}
  async function load(){
    const current=++request;status.textContent='Consultando tareas…';section.setAttribute('aria-busy','true');
    try{
      const response=await fetch(`/api/v1/my-deposit-tasks?page=${page}&size=10`,{headers:auth.headers({Accept:'application/json'})});
      const body=await response.json();if(!response.ok)throw Error(body.detail||'No se pudieron consultar las tareas.');if(current!==request)return;
      pages=body.pages;list.replaceChildren();
      for(const task of body.items){
        const card=text('article','');card.className='panel';const heading=text('h3',task.title);heading.className='truncate';heading.title=task.title;card.append(heading);
        card.append(text('p',`${task.status==='IN_REVIEW'?'En revisión':'Borrador'} · ${task.completionPercent}% del checklist automático`),text('p',task.nextAction));
        if(task.status==='IN_REVIEW')card.append(text('p','El depósito no es editable mientras curación lo revisa.'));
        const pending=text('ul','');for(const check of task.pending){const item=text('li',`${check.label}: ${check.required?'necesario':'recomendación'}. ${check.explanation||''}`);pending.append(item);}card.append(pending);
        const link=text('a',task.status==='IN_REVIEW'?'Ver depósito':'Completar depósito');link.className='secondary';link.href='resource.html?id='+encodeURIComponent(task.resourceId);card.append(link);list.append(card);
      }
      if(!body.total)list.append(text('p','No tienes borradores ni depósitos pendientes de revisión. Los publicados siguen disponibles en Mis depósitos.'));
      status.textContent=body.total?`${body.total} depósitos activos · Página ${body.page+1} de ${body.pages}`:'Sin tareas de depósito';
      document.querySelector('#task-previous').disabled=page===0;document.querySelector('#task-next').disabled=page+1>=pages;
    }catch(error){if(current!==request)return;list.replaceChildren(text('p',error.message));status.textContent='No se pudieron cargar las tareas. Puedes reintentar.';}
    finally{if(current===request)section.setAttribute('aria-busy','false');}
  }
  document.querySelector('#task-previous').onclick=()=>{if(page>0){page--;load();}};
  document.querySelector('#task-next').onclick=()=>{if(page+1<pages){page++;load();}};
  document.querySelector('#refresh-tasks').onclick=load;load();
})();
