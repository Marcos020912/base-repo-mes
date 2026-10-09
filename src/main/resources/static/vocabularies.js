/* Searchable suggestions; the original input/value remains the form control. */
(async () => {
  try {
    const response=await fetch('/api/v1/scientific/vocabularies',{headers:auth.headers()});
    if(!response.ok)return;
    const vocabulary=await response.json();let sequence=0;
    for(const [name,key] of [['licenseId','licenses'],['discipline','disciplines']]){
      const entries=vocabulary[key];if(!Array.isArray(entries))continue;
      for(const input of document.querySelectorAll(`input[name="${name}"]`)){
        if(input.readOnly||input.disabled)continue;
        const id=`vocabulary-${name}-${++sequence}`,wrapper=document.createElement('span');wrapper.className='vocabulary-control';input.before(wrapper);wrapper.append(input);
        const caption=input.closest('label')?.querySelector('span');if(caption){caption.id ||=id+'-label';input.setAttribute('aria-labelledby',caption.id);}
        input.setAttribute('role','combobox');input.setAttribute('aria-autocomplete','list');input.setAttribute('aria-expanded','false');input.setAttribute('aria-controls',id);input.autocomplete='off';
        const toggle=document.createElement('button');toggle.type='button';toggle.className='vocabulary-toggle';toggle.textContent='▾';uiI18n.attribute(toggle,'aria-label','vocabulary.toggle');toggle.setAttribute('aria-controls',id);toggle.setAttribute('aria-expanded','false');
        const list=document.createElement('span');list.id=id;list.className='vocabulary-options';list.setAttribute('role','listbox');list.hidden=true;uiI18n.attribute(list,'aria-label',name==='discipline'?'wizard.discipline':'wizard.license');wrapper.append(toggle,list);let active=-1,options=[];
        const normalize=text=>String(text).normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase();
        function close(){list.hidden=true;input.setAttribute('aria-expanded','false');toggle.setAttribute('aria-expanded','false');input.removeAttribute('aria-activedescendant');active=-1;}
        function choose(value){input.value=value;input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));close();input.focus();}
        function highlight(index){active=index;options.forEach((option,i)=>option.setAttribute('aria-selected',String(i===active)));if(options[active]){input.setAttribute('aria-activedescendant',options[active].id);options[active].scrollIntoView({block:'nearest'});}}
        function open(all=false){list.replaceChildren();active=-1;input.removeAttribute('aria-activedescendant');options=[];for(const value of entries.filter(value=>typeof value==='string'&&(all||normalize(value).includes(normalize(input.value))))){const option=document.createElement('button');option.type='button';option.setAttribute('role','option');option.setAttribute('aria-selected','false');option.tabIndex=-1;option.id=id+'-'+options.length;option.textContent=value;option.addEventListener('mousedown',event=>event.preventDefault());option.onclick=()=>choose(value);list.append(option);options.push(option);}if(!options.length){const message=document.createElement('span');message.className='vocabulary-empty';uiI18n.set(message,'vocabulary.noMatches');list.append(message);}list.hidden=false;input.setAttribute('aria-expanded','true');toggle.setAttribute('aria-expanded','true');}
        input.addEventListener('input',()=>open());
        toggle.onclick=()=>{if(list.hidden){input.focus();open(true);}else close();};
        input.addEventListener('keydown',event=>{if(event.key==='Escape'){close();return;}if(['ArrowDown','ArrowUp'].includes(event.key)){event.preventDefault();if(list.hidden)open(true);if(options.length)highlight(event.key==='ArrowDown'?(active+1)%options.length:(active<=0?options.length-1:active-1));}else if(event.key==='Enter'&&!list.hidden){event.preventDefault();if(active>=0)choose(options[active].textContent);else close();}else if(event.key==='Tab')close();});
        wrapper.addEventListener('focusout',event=>{if(!wrapper.contains(event.relatedTarget))close();});
        document.addEventListener('click',event=>{if(!wrapper.contains(event.target))close();});
        input.form?.addEventListener('reset',close);
      }
    }
  } catch (_) { /* Optional suggestions never prevent manual entry. */ }
})();
