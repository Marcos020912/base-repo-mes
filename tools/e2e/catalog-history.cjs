#!/usr/bin/env node
/* Isolated browser UI history test. API/auth are fixtures, not security or search-backend tests. */
const fs=require('node:fs'),path=require('node:path'),http=require('node:http');
const puppeteer=require('../a11y/node_modules/puppeteer-core');
const root=path.resolve(__dirname,'../../src/main/resources/static');
const server=http.createServer((req,res)=>{
  const url=new URL(req.url,'http://localhost');
  if(url.pathname==='/auth.js'){res.setHeader('Content-Type','application/javascript');res.end("window.auth={requireLogin(){},user(){return {username:'fixture',role:'USER'}},headers(v){return v},logout(){}};window.toast={error(){}};");return;}
  if(url.pathname.startsWith('/api/')){
    res.setHeader('Content-Type','application/json');
    if(url.pathname.endsWith('/facets'))return res.end(JSON.stringify({author:[{value:'Ada',count:60}]}));
    if(url.pathname.endsWith('/metrics'))return res.end(JSON.stringify({publishedVersions:60,openPolicyVersions:60,publishedCollections:0,measuredAt:new Date().toISOString(),definitions:{}}));
    const page=Number(url.searchParams.get('page')||0);return res.end(JSON.stringify({items:[{id:'fixture',title:url.searchParams.get('q')||'Todos',authors:['Ada'],accessLevel:'OPEN',type:'IMAGE',year:'2026'}],page,pages:3,total:60}));
  }
  const file=path.resolve(root,'.'+url.pathname);if(!file.startsWith(root+path.sep)||!fs.existsSync(file)||!fs.statSync(file).isFile()){res.writeHead(404).end();return;}
  res.setHeader('Content-Type',({'.js':'application/javascript','.css':'text/css','.html':'text/html'})[path.extname(file)]||'application/octet-stream');fs.createReadStream(file).pipe(res);
});
function assert(value,message){if(!value)throw Error(message);}
(async()=>{let browser;try{
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const base='http://127.0.0.1:'+server.address().port;
  browser=await puppeteer.launch({executablePath:process.env.CHROME_BIN||'/usr/bin/google-chrome',headless:true,args:['--no-sandbox']});
  for(const mode of ['public','private']){
    const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
    const route=mode==='public'?'public.html':'index.html',input=mode==='public'?'#public-q':'#filter',sort=mode==='public'?'#public-sort':'#sort-order',cards=mode==='public'?'#public-results':'#resource-list';
    await page.goto(base+'/'+route+'?q=initial&author=Ada&type=IMAGE&page=1&sort=oldest',{waitUntil:'networkidle0'});
    assert(await page.$eval(input,n=>n.value)==='initial','Consulta inicial no restaurada');
    await page.$eval(input,n=>{n.value='next';n.dispatchEvent(new Event('input',{bubbles:true}));});
    await page.waitForFunction(()=>new URLSearchParams(location.search).get('q')==='next');await page.waitForFunction(selector=>document.querySelector(selector).textContent.includes('next'),{},cards);
    assert(new URL(page.url()).searchParams.get('page')==='0','Cambiar consulta no reinició página');
    await page.goBack();await page.waitForFunction(selector=>document.querySelector(selector).value==='initial',{},input);
    await page.waitForFunction(selector=>document.querySelector(selector).textContent.includes('initial'),{},cards);
    assert(new URL(page.url()).searchParams.get('page')==='1','Atrás perdió paginación');assert(await page.$eval(sort,n=>n.value)==='oldest','Atrás perdió orden');
    await page.goForward();await page.waitForFunction(selector=>document.querySelector(selector).value==='next',{},input);
    await page.reload({waitUntil:'networkidle0'});assert(await page.$eval(input,n=>n.value)==='next','Recarga perdió búsqueda compartida');
    await page.goto(base+'/'+route,{waitUntil:'networkidle0'});
    await page.select(sort,'oldest');await page.waitForFunction(()=>new URLSearchParams(location.search).get('sort')==='oldest');
    await page.goBack();await page.waitForFunction(selector=>document.querySelector(selector).value==='newest',{},sort);
    assert(await page.$eval(input,n=>n.value)==='','Atrás no limpió consulta ausente');
    if(mode==='public'){
      await page.waitForSelector('.public-facet-option');await page.click('.public-facet-option');await page.waitForFunction(()=>new URLSearchParams(location.search).get('author')==='Ada');
      await page.goBack();await page.waitForFunction(()=>[...document.querySelectorAll('.public-facet-option')].every(n=>n.getAttribute('aria-pressed')==='false'));
      assert(new URL(page.url()).searchParams.get('author')==='','Atrás no restauró faceta vacía');
    }
    assert(!errors.length,'Errores JS: '+errors.join('; '));await page.close();console.log('Historial '+mode+' OK: filtros, orden, página, Atrás/Adelante, valores ausentes y recarga.');
  }
}catch(e){console.error(e.stack);process.exitCode=1;}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}})();
