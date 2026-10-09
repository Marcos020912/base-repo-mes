/* Local usage, deliberately not represented as certified COUNTER statistics. */
(() => {
  for(const root of document.querySelectorAll('[data-usage]')) {
    const editor=root.dataset.usage==='report';
    const id=root.dataset.usage==='resource'?(new URLSearchParams(location.search).get('id')||(location.pathname.startsWith('/datasets/')?decodeURIComponent(location.pathname.slice(10)):null)):null;
    let report=null;
    const status=document.createElement('p');status.setAttribute('role','status');
    const list=document.createElement('dl');list.className='metadata-list';
    const heading=document.createElement('h2');uiI18n.set(heading,'usage.heading');
    const note=document.createElement('p');note.className='muted';uiI18n.set(note,'usage.note');
    root.append(heading,status,list,note);
    const form=document.createElement('form'),from=document.createElement('input'),until=document.createElement('input');
    let exportButton;
    if(editor) {
      for(const [input,key]of [[from,'usage.from'],[until,'usage.until']]) {
        input.type='date';input.required=true;input.max=new Date().toISOString().slice(0,10);
        input.min=new Date(Date.now()-89*86400000).toISOString().slice(0,10);
        input.value=new Date(Date.now()-(input===from?29:0)*86400000).toISOString().slice(0,10);
        const label=document.createElement('label'),span=document.createElement('span');uiI18n.set(span,key);label.append(span,input);form.append(label);
      }
      form.className='form-grid';const button=document.createElement('button');button.type='submit';button.className='secondary';uiI18n.set(button,'usage.refresh');form.append(button);root.insertBefore(form,status);
      exportButton=document.createElement('button');exportButton.className='secondary';uiI18n.set(exportButton,'usage.export');exportButton.disabled=true;root.append(exportButton);
      exportButton.onclick=()=>{if(!report)return;const url=URL.createObjectURL(new Blob([JSON.stringify(report,null,2)],{type:'application/json'}));const link=document.createElement('a');link.href=url;link.download=`reduniv-usage-${report.from}-${report.until}.json`;link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
      form.onsubmit=event=>{event.preventDefault();load();};
    }
    function render() {
      list.replaceChildren();
      for(const [key,label]of [['views','usage.views'],['downloads','usage.downloads']]) {
        const dt=document.createElement('dt'),dd=document.createElement('dd');uiI18n.set(dt,label);dd.textContent=new Intl.NumberFormat(uiI18n.locale).format(report[key]);list.append(dt,dd);
      }
      uiI18n.set(status,report.collectionEnabled?'usage.period':'usage.disabled',{from:report.from,until:report.until});
    }
    let generation=0;
    async function load(){const current=++generation;report=null;list.replaceChildren();if(exportButton)exportButton.disabled=true;uiI18n.set(status,'usage.loading');
      try {
        const params=new URLSearchParams();if(id)params.set('resourceId',id);if(editor){params.set('from',from.value);params.set('until',until.value);}
        const response=await fetch((editor?'/api/v1/scientific/operations/usage':'/api/v1/public/usage')+'?'+params,{headers:editor?auth.headers({Accept:'application/json'}):{Accept:'application/json'},cache:'no-store'});
        if(!response.ok)throw Error();const body=await response.json();
        if(body.schema!=='reduniv.local-usage.v1'||!['views','downloads'].every(key=>Number.isSafeInteger(body[key])&&body[key]>=0))throw Error();
        if(current!==generation)return;report=body;render();if(exportButton)exportButton.disabled=false;
      }catch{if(current===generation){report=null;list.replaceChildren();uiI18n.set(status,'usage.failed');}}
    }
    window.addEventListener('ui-locale-changed',()=>{if(report)render();});
    load();
  }
})();
