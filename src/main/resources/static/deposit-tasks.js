/* Author-only task projection; errors do not masquerade as an empty queue. */
(() => {
  const section=document.querySelector('#deposit-tasks'),list=document.querySelector('#task-list'),status=document.querySelector('#task-status');
  let page=0,pages=0,request=0;
  function text(tag,value){const node=document.createElement(tag);node.textContent=value;return node;}
  function ui(tag,key,params={}){const node=document.createElement(tag);uiI18n.set(node,key,params);return node;}
  const qualityCodes=new Set(['title','authors','version','license','institution','methodology','description','files','doi','orcid','ror','summary','coverage']);
  const profileFields=new Set(['summary','language','discipline','keywords','productionDescription','processingDescription','processingTools','temporalStart','temporalEnd','geographicCoverage','translations']);
  function checkLabel(check){if(qualityCodes.has(check.code))return ui('span',`quality.${check.code}`);const field=check.code?.startsWith('profile:')?check.code.slice(8):null;return profileFields.has(field)?ui('span',`quality.profile.${field}`):text('span',check.label);}
  async function load(){
    const current=++request;uiI18n.set(status,'deposit.tasksLoading');section.setAttribute('aria-busy','true');
    try{
      const response=await fetch(`/api/v1/my-deposit-tasks?page=${page}&size=10`,{headers:auth.headers({Accept:'application/json'})});
      const body=await response.json();if(!response.ok){if(body.detail)throw Error(body.detail);throw uiI18n.error('deposit.tasksFailed');}if(current!==request)return;
      if(!Array.isArray(body.items)||!Number.isInteger(body.pages)||!Number.isInteger(body.page)||!Number.isFinite(body.total))throw uiI18n.error('deposit.tasksFailed');
      pages=body.pages;list.replaceChildren();
      for(const task of body.items){
        const card=text('article','');card.className='panel';const heading=text('h3',task.title);heading.className='truncate';heading.title=task.title;card.append(heading);
        const progress=document.createElement('p');progress.append(ui('span',task.status==='IN_REVIEW'?'deposit.review':'deposit.draft'),document.createTextNode(' · '),ui('span','deposit.checklistPercent',{percent:task.completionPercent}));card.append(progress,ui('p',task.status==='IN_REVIEW'?'deposit.waitReview':task.blockers?.length?'deposit.completeMetadata':'deposit.reviewSubmit'));
        if(task.status==='IN_REVIEW')card.append(ui('p','deposit.locked'));
        const pending=text('ul','');for(const check of task.pending||[]){const item=document.createElement('li');item.append(checkLabel(check),document.createTextNode(': '),ui('span',check.required?'deposit.required':'deposit.recommended'),document.createTextNode('. '),qualityCodes.has(check.code)?ui('span',`quality.${check.code}.explanation`):check.code?.startsWith('profile:')?ui('span','quality.profileExplanation'):text('span',check.explanation||''));pending.append(item);}card.append(pending);
        const link=ui('a',task.status==='IN_REVIEW'?'deposit.view':'deposit.complete');link.className='secondary';link.href='resource.html?id='+encodeURIComponent(task.resourceId);card.append(link);list.append(card);
      }
      if(!body.total)list.append(ui('p','deposit.noTasks'));
      uiI18n.set(status,body.total?'deposit.taskTotal':'deposit.noTaskStatus',{total:body.total,page:body.page+1,pages:body.pages});
      document.querySelector('#task-previous').disabled=page===0;document.querySelector('#task-next').disabled=page+1>=pages;
    }catch(error){if(current!==request)return;const message=document.createElement('p');uiI18n.showError(message,error);list.replaceChildren(message);uiI18n.set(status,'deposit.taskRetry');}
    finally{if(current===request)section.setAttribute('aria-busy','false');}
  }
  document.querySelector('#task-previous').onclick=()=>{if(page>0){page--;load();}};
  document.querySelector('#task-next').onclick=()=>{if(page+1<pages){page++;load();}};
  document.querySelector('#refresh-tasks').onclick=load;load();
})();
