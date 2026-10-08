(() => {
  const catalogues=window.uiCatalogues;
  let locale='es';
  try{const stored=localStorage.getItem('reduniv-ui-locale');if(Object.hasOwn(catalogues,stored))locale=stored;}catch{/* Storage can be disabled. */}
  for(const catalogue of Object.values(catalogues))Object.freeze(catalogue);
  function t(key){const text=catalogues[locale][key];if(typeof text!=='string')throw new Error(`Unknown UI catalogue key: ${key}`);return text;}
  function set(target,key){target.dataset.i18n=key;target.lang=locale;target.textContent=t(key);}
  function plain(target,text){delete target.dataset.i18n;target.removeAttribute('lang');target.textContent=text;}
  function error(key){const failure=new Error(t(key));failure.i18nKey=key;return failure;}
  function showError(target,failure){if(failure.i18nKey)set(target,failure.i18nKey);else plain(target,failure.message);}
  function render(){if(document.documentElement.hasAttribute('data-ui-page-localized'))document.documentElement.lang=locale;document.querySelectorAll('[data-i18n]').forEach(element=>set(element,element.dataset.i18n));document.querySelectorAll('[data-i18n-aria-label]').forEach(element=>{element.lang=locale;element.setAttribute('aria-label',t(element.dataset.i18nAriaLabel));});document.querySelectorAll('[data-i18n-alt]').forEach(element=>{element.lang=locale;element.alt=t(element.dataset.i18nAlt);});document.querySelectorAll('[data-ui-locale]').forEach(select=>select.value=locale);}
  function change(next){if(!Object.hasOwn(catalogues,next))return false;locale=next;try{localStorage.setItem('reduniv-ui-locale',locale);}catch{/* In-memory selection still works. */}render();window.dispatchEvent(new CustomEvent('ui-locale-changed',{detail:{locale}}));return true;}
  window.uiI18n=Object.freeze({t,set,plain,error,showError,change,get locale(){return locale;}});
  document.querySelectorAll('[data-ui-locale]').forEach(select=>select.addEventListener('change',()=>change(select.value)));
  render();
})();
