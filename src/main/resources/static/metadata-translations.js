/* Shared editor for declared translations. Never generates translations automatically. */
window.metadataTranslations = {
  mount(host, hidden) {
    const list=document.createElement('div');list.className='related-editor';
    const add=document.createElement('button');add.type='button';add.className='secondary';uiI18n.set(add,'metadataTranslations.add');
    host.append(list,add);
    const rows=[];
    function sync(notify=true) {
      const result={};const seen=new Set();
      for(const row of rows) {
        const key=row.language.value.trim();
        row.language.setCustomValidity(seen.has(key.toLowerCase())?uiI18n.t('metadataTranslations.duplicate'):'');
        seen.add(key.toLowerCase());
        row.title.setCustomValidity(!row.title.value.trim()&&!row.summary.value.trim()?uiI18n.t('metadataTranslations.required'):'');
        if(key)result[key]={title:row.title.value.trim(),summary:row.summary.value.trim()};
      }
      hidden.value=JSON.stringify(result);add.disabled=rows.length>=10;
      if(notify)hidden.dispatchEvent(new Event('input',{bubbles:true}));
    }
    function row(value={},lang='') {
      if(rows.length>=10)return;
      const box=document.createElement('fieldset');box.className='translation-row';
      const legend=document.createElement('legend');uiI18n.set(legend,'metadataTranslations.legend');box.append(legend);
      const controls={box};
      for(const [key,text,limit] of [['language','metadataTranslations.language',35],['title','metadataTranslations.title',500],['summary','metadataTranslations.summary',5000]]) {
        const label=document.createElement('label');const caption=document.createElement('span');uiI18n.set(caption,text);label.append(caption);
        const input=document.createElement(key==='summary'?'textarea':'input');input.maxLength=limit;
        input.value=key==='language'?lang:(value[key]||'');
        if(key==='language'){input.required=true;input.pattern='[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8}){0,3}';}
        input.addEventListener('input',()=>sync());label.append(input);box.append(label);controls[key]=input;
      }
      const remove=document.createElement('button');remove.type='button';remove.className='danger';uiI18n.set(remove,'metadataTranslations.remove');
      remove.onclick=()=>{rows.splice(rows.indexOf(controls),1);box.remove();sync();add.focus();};box.append(remove);
      rows.push(controls);list.append(box);sync(false);return controls;
    }
    add.onclick=()=>{const added=row();sync();added?.language.focus();};
    const editor={set(values={}){rows.splice(0);list.replaceChildren();for(const [lang,value] of Object.entries(values).slice(0,10))row(value||{},lang);sync(false);},get(){sync(false);return JSON.parse(hidden.value||'{}');}};
    window.addEventListener('ui-locale-changed',()=>sync(false));editor.set(JSON.parse(hidden.value||'{}'));return editor;
  }
};
