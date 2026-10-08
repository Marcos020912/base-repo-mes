/* Shared editor for declared translations. Never generates translations automatically. */
window.metadataTranslations = {
  mount(host, hidden) {
    const list=document.createElement('div');list.className='related-editor';
    const add=document.createElement('button');add.type='button';add.className='secondary';add.textContent='+ Agregar traducción';
    host.append(list,add);
    const rows=[];
    function sync(notify=true) {
      const result={};const seen=new Set();
      for(const row of rows) {
        const key=row.language.value.trim();
        row.language.setCustomValidity(seen.has(key.toLowerCase())?'El idioma está duplicado.':'');
        seen.add(key.toLowerCase());
        row.title.setCustomValidity(!row.title.value.trim()&&!row.summary.value.trim()?'Indique título o resumen para esta traducción.':'');
        if(key)result[key]={title:row.title.value.trim(),summary:row.summary.value.trim()};
      }
      hidden.value=JSON.stringify(result);add.disabled=rows.length>=10;
      if(notify)hidden.dispatchEvent(new Event('input',{bubbles:true}));
    }
    function row(value={},lang='') {
      if(rows.length>=10)return;
      const box=document.createElement('fieldset');box.className='translation-row';
      const legend=document.createElement('legend');legend.textContent='Traducción declarada';box.append(legend);
      const controls={box};
      for(const [key,text,limit] of [['language','Idioma (ej. en o pt-BR)',35],['title','Título traducido',500],['summary','Resumen traducido',5000]]) {
        const label=document.createElement('label');label.textContent=text;
        const input=document.createElement(key==='summary'?'textarea':'input');input.maxLength=limit;
        input.value=key==='language'?lang:(value[key]||'');
        if(key==='language'){input.required=true;input.pattern='[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8}){0,3}';}
        input.addEventListener('input',()=>sync());label.append(input);box.append(label);controls[key]=input;
      }
      const remove=document.createElement('button');remove.type='button';remove.className='danger';remove.textContent='Quitar traducción';
      remove.onclick=()=>{rows.splice(rows.indexOf(controls),1);box.remove();sync();add.focus();};box.append(remove);
      rows.push(controls);list.append(box);sync(false);return controls;
    }
    add.onclick=()=>{const added=row();sync();added?.language.focus();};
    const editor={set(values={}){rows.splice(0);list.replaceChildren();for(const [lang,value] of Object.entries(values).slice(0,10))row(value||{},lang);sync(false);},get(){sync(false);return JSON.parse(hidden.value||'{}');}};
    editor.set(JSON.parse(hidden.value||'{}'));return editor;
  }
};
