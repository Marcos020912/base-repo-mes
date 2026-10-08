/* Private assessment editor. Public dataset pages never load this module. */
window.scientificPrivacy = (() => {
  async function request(path,options={}) {
    const response=await fetch(path,{...options,headers:auth.headers({'Content-Type':'application/json',...options.headers})});
    const body=await response.json().catch(()=>({}));
    if(!response.ok){if(body.detail||body.message)throw new Error(body.detail||body.message);throw uiI18n.error('privacy.failed');}
    return body;
  }
  function protectNote(select,note) {const update=()=>note.required=Boolean(select.value&&select.value!=='NONE');select.addEventListener('change',update);update();return update;}
  const wizard=document.querySelector('#wizard-form');
  if(wizard) {
    const select=wizard.elements.namedItem('privacyClassification');select.required=true;
    protectNote(select,wizard.elements.namedItem('privacyNote'));
    request('/api/v1/scientific/privacy-policy').then(policy=>{
      select.required=policy.required!==false;
      uiI18n.set(document.querySelector('#privacy-policy-status'),select.required?'privacy.required':'privacy.recommended');
    }).catch(()=>uiI18n.set(document.querySelector('#privacy-policy-status'),'privacy.policyFailed'));
  }
  const form=document.querySelector('#privacy-form');
  if(form) {
    const id=new URLSearchParams(location.search).get('id');const panel=document.querySelector('#privacy-panel');
    const dialog=document.querySelector('#privacy-modal');const open=document.querySelector('#edit-privacy');
    let current=null;
    const update=protectNote(form.elements.classification,form.elements.assessmentNote);
    async function load() {
      try {
        const [assessment,metadata]=await Promise.all([request(`/api/v1/scientific/${encodeURIComponent(id)}/privacy`),request(`/api/v1/scientific/${encodeURIComponent(id)}`)]);
        current=assessment;panel.hidden=false;open.hidden=metadata.status!=='DRAFT';
        uiI18n.set(document.querySelector('#privacy-status'),assessment.classification?'privacy.status':'privacy.unassessed',{classification:assessment.classification,reviewState:assessment.reviewState});
        const notes=document.querySelector('#privacy-notes');notes.replaceChildren();for(const note of [assessment.assessmentNote,assessment.reviewNote].filter(Boolean))notes.append(document.createTextNode(note+'\n'));if(assessment.reviewedBy){const reviewer=document.createElement('span');uiI18n.set(reviewer,'privacy.reviewedBy',{username:assessment.reviewedBy});notes.append(reviewer);}
      } catch {panel.hidden=true;}
    }
    open.onclick=()=>{form.elements.classification.value=current?.classification||'';form.elements.assessmentNote.value=current?.assessmentNote||'';update();dialog.showModal();};
    form.addEventListener('submit',async event=>{
      event.preventDefault();const button=form.querySelector('[type=submit]');button.disabled=true;
      try {await request(`/api/v1/scientific/${encodeURIComponent(id)}/privacy`,{method:'PUT',body:JSON.stringify({classification:form.elements.classification.value,assessmentNote:form.elements.assessmentNote.value,revision:current?.revision??null})});dialog.close();toast.successKey('privacy.saved');await load();}
      catch(error){toast.errorObject(error);}finally{button.disabled=false;}
    });load();
  }
  const reviewForm=document.querySelector('#privacy-review-form');
  if(reviewForm)reviewForm.addEventListener('submit',async event=>{
    event.preventDefault();const button=reviewForm.querySelector('[type=submit]');button.disabled=true;
    try {await request(`/api/v1/scientific/${encodeURIComponent(reviewForm.elements.resourceId.value)}/privacy/review`,{method:'POST',body:JSON.stringify({approved:reviewForm.elements.decision.value==='APPROVED',reviewNote:reviewForm.elements.reviewNote.value,revision:Number(reviewForm.elements.revision.value)})});document.querySelector('#privacy-review-modal').close();toast.successKey('privacy.reviewSaved');document.querySelector('#refresh-reviews').click();}
    catch(error){toast.errorObject(error);}finally{button.disabled=false;}
  });
  document.querySelectorAll('[data-close-privacy]').forEach(button=>button.onclick=()=>button.closest('dialog').close());
  return {
    async saveWizard(id) {
      const classification=wizard.elements.namedItem('privacyClassification').value;
      if(classification)await request(`/api/v1/scientific/${encodeURIComponent(id)}/privacy`,{method:'PUT',body:JSON.stringify({classification,assessmentNote:wizard.elements.namedItem('privacyNote').value})});
    },
    openReview(id,assessment) {
      reviewForm.elements.resourceId.value=id;reviewForm.elements.revision.value=assessment.revision;
      reviewForm.elements.decision.value='CHANGES_REQUIRED';reviewForm.elements.reviewNote.value='';
      document.querySelector('#privacy-review-modal').showModal();
    }
  };
})();
