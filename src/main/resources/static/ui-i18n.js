(() => {
  const catalogues=window.uiCatalogues;
  const textBindings=new Map(), attributeBindings=new Map();
  const pickers=[...document.querySelectorAll('[data-ui-locale]')];
  let locale='es';
  try{const stored=localStorage.getItem('reduniv-ui-locale');if(Object.hasOwn(catalogues,stored))locale=stored;}catch{/* Storage can be disabled. */}
  for(const catalogue of Object.values(catalogues))Object.freeze(catalogue);
  function t(key,params={}){const value=catalogues[locale][key];if(typeof value!=='string')throw new Error(`Unknown UI catalogue key: ${key}`);return value.replace(/\{(\w+)\}/g,(_,name)=>{if(!Object.hasOwn(params,name))throw new Error(`Missing UI parameter: ${name}`);return String(params[name]);});}
  function set(target,key,params={}){
    if(target.children.length)throw new Error(`UI text bindings require a leaf node (${target.tagName}#${target.id || '-'}, key: ${key})`);
    const value=t(key,params);textBindings.set(target,{key,params:{...params}});target.dataset.i18n=key;target.lang=locale;target.textContent=value;
  }
  function attribute(target,name,key,params={}){
    if(!['alt','aria-label','placeholder'].includes(name))throw new Error('Unsupported UI attribute binding');
    const value=t(key,params);if(!attributeBindings.has(target))attributeBindings.set(target,new Map());attributeBindings.get(target).set(name,{key,params:{...params}});
    target.lang=locale;target.setAttribute(name,value);
  }
  function plain(target,value){textBindings.delete(target);delete target.dataset.i18n;target.removeAttribute('lang');target.textContent=value;}
  function error(key,params={}){const failure=new Error(t(key,params));failure.i18nKey=key;failure.i18nParams={...params};return failure;}
  function showError(target,failure){if(target.isConnected&&typeof toast!=='undefined'){target.classList.remove('error');plain(target,'');toast.errorObject(failure);return;}if(failure.i18nKey)set(target,failure.i18nKey,failure.i18nParams);else plain(target,failure.message);}
  function render(){
    if(document.documentElement.hasAttribute('data-ui-page-localized'))document.documentElement.lang=locale;
    for(const [target,binding] of textBindings){if(target.isConnected)set(target,binding.key,binding.params);else textBindings.delete(target);}
    for(const [target,values] of attributeBindings){if(target.isConnected)for(const [name,binding] of values)attribute(target,name,binding.key,binding.params);else attributeBindings.delete(target);}
    pickers.forEach(select=>select.value=locale);
  }
  function change(next){if(!Object.hasOwn(catalogues,next))return false;locale=next;try{localStorage.setItem('reduniv-ui-locale',locale);}catch{/* In-memory selection still works. */}render();window.dispatchEvent(new CustomEvent('ui-locale-changed',{detail:{locale}}));return true;}
  // Only trusted initial markup is declarative. Later author content is never scanned.
  document.querySelectorAll('[data-i18n]').forEach(target=>set(target,target.dataset.i18n));
  document.querySelectorAll('[data-i18n-aria-label]').forEach(target=>attribute(target,'aria-label',target.dataset.i18nAriaLabel));
  document.querySelectorAll('[data-i18n-alt]').forEach(target=>attribute(target,'alt',target.dataset.i18nAlt));
  document.querySelectorAll('[data-i18n-placeholder]').forEach(target=>attribute(target,'placeholder',target.dataset.i18nPlaceholder));
  window.uiI18n=Object.freeze({t,set,attribute,plain,error,showError,change,get locale(){return locale;}});
  pickers.forEach(select=>select.addEventListener('change',()=>change(select.value)));
  render();
  new MutationObserver(changes=>{
    if(!changes.some(change=>change.removedNodes.length))return;
    for(const target of textBindings.keys())if(!target.isConnected)textBindings.delete(target);
    for(const target of attributeBindings.keys())if(!target.isConnected)attributeBindings.delete(target);
  }).observe(document.body,{childList:true,subtree:true});
})();
