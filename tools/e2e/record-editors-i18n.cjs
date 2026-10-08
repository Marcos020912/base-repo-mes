#!/usr/bin/env node
// Loopback HTTP UI fixtures: verifies translations and preservation, not server authorization.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const puppeteer=require('../a11y/node_modules/puppeteer-core');
const root=path.resolve(__dirname,'../../src/main/resources/static');
const calls=[];let versionsFail=false;
const relation={kind:'ARTICLE',identifierType:'DOI',relationType:'Cites',identifier:'10.1234/original',title:'Título <original>'};
const creator={creatorId:7,givenName:'Autora <original>',orcid:'0000-0001-2345-6789',orcidAuthenticated:false,affiliations:[{institution:'Institución original',ror:'012345678'}]};
const funding={funderName:'Institución original',funderRor:'012345678',awardNumber:'Número original',awardTitle:'Proyecto original'};
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://fixture');
 if(url.pathname.startsWith('/api/')){
  calls.push({method:req.method,path:url.pathname});res.setHeader('Content-Type','application/json');
  if(url.pathname.endsWith('/versions')){if(versionsFail){res.writeHead(503);res.end('{}');return;}res.end(JSON.stringify({items:[{id:'original',version:'1.0 original',current:true,status:'PUBLISHED',publishedAt:'2026-10-08',doi:'10.1234/original'},{id:'old',current:false,status:'WITHDRAWN'}],page:0,pages:2,total:22,newerPublicationAvailable:true,latestPublishedId:'latest'}));return;}
  if(req.method==='PUT'){let body='';req.on('data',chunk=>body+=chunk);req.on('end',()=>{calls[calls.length-1].body=JSON.parse(body);res.end('{}');});return;}
  if(url.pathname.endsWith('/ror/search')){res.end(JSON.stringify([{name:'Institución ROR original',ror:'012345678'}]));return;}
  const body=url.pathname.endsWith('/creators')?[creator]:url.pathname.endsWith('/orcid/status')?{enabled:true}:url.pathname.endsWith('/relations')?[relation]:url.pathname.endsWith('/funding')?[funding]:url.pathname.endsWith('/my-dataresources')?[{id:'original'}]:{status:'DRAFT'};
  res.end(JSON.stringify(body));return;
 }
 if(url.pathname==='/fixture.html'){
  res.setHeader('Content-Type','text/html; charset=utf-8');res.end(`<!doctype html><html lang="es"><head><title>Fixture</title></head><body><main><section id="history-panel"></section><div id="version-links"></div><p id="versions-status"></p><ol id="versions-list"></ol><nav id="versions-pagination"></nav></main><script src="/ui-locales.js"></script><script src="/ui-i18n.js"></script><script>window.auth={headers:()=>({})};window.toast={successKey:key=>window.lastToast=uiI18n.t(key),errorObject:error=>window.lastToast=error.i18nKey?uiI18n.t(error.i18nKey):error.message};</script><script src="/relations.js"></script><script src="/funding.js"></script><script src="/creators.js"></script><script src="/public-versions.js"></script></body></html>`);return;
 }
 const name=url.pathname.slice(1);if(!/^[\w.-]+$/.test(name)||!fs.existsSync(path.join(root,name))){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type','application/javascript; charset=utf-8');res.end(fs.readFileSync(path.join(root,name)));
});
(async()=>{await new Promise(r=>server.listen(0,'127.0.0.1',r));const browser=await puppeteer.launch({executablePath:'/usr/bin/google-chrome',headless:true,args:['--no-sandbox']});try{
 const page=await browser.newPage(),errors=[];page.on('pageerror',error=>errors.push(error.message));await page.goto(`http://127.0.0.1:${server.address().port}/fixture.html?id=original`);
 await page.waitForFunction(()=>document.querySelectorAll('.section-heading button:not([hidden])').length===3&&document.querySelector('#versions-list li'));
 const before=calls.length;await page.evaluate(()=>uiI18n.change('en'));
 assert.equal(await page.$eval('#versions-status',node=>node.textContent),'22 public versions · Page 1 of 2');
 assert.equal(await page.$eval('#versions-list a',node=>node.textContent),'Version 1.0 original · This record');
 assert.equal(await page.$eval('#family-version-warning a',node=>node.getAttribute('href')),'/datasets/latest');
 assert.equal(await page.$eval('.related-resource a',node=>node.textContent),'Título <original>');
 assert.equal(calls.length,before);
 for(const [index,name,value] of [[0,'title','Título <original>'],[1,'awardTitle','Proyecto original']]){
  await page.$$eval('.section-heading button',(nodes,index)=>nodes[index].click(),index);
  await page.waitForSelector('dialog[open]');await page.$eval(`dialog[open] [name=${name}]`,node=>node.value+=' unchanged');
  await page.evaluate(()=>uiI18n.change('es'));
  assert.equal(await page.$eval(`dialog[open] [name=${name}]`,node=>node.value),value+' unchanged');
  assert.equal(await page.$eval('dialog[open] [name=identifierType]',node=>node.value).catch(()=>null),index===0?'DOI':null);
  await page.evaluate(()=>uiI18n.change('en'));const puts=calls.filter(call=>call.method==='PUT').length;
  await page.$eval('dialog[open] form',form=>form.requestSubmit());await page.waitForFunction(()=>!document.querySelector('dialog[open]'));
  assert.equal(calls.filter(call=>call.method==='PUT').length,puts+1);
  const sent=calls.filter(call=>call.method==='PUT').at(-1).body[0];assert.equal(sent[name],value+' unchanged');
  if(index===0){assert.equal(sent.kind,'ARTICLE');assert.equal(sent.relationType,'Cites');assert.equal(sent.identifier,'10.1234/original');}
 }
 await page.$$eval('.section-heading button',nodes=>nodes[2].click());await page.waitForSelector('dialog[open]');
 await page.$eval('dialog[open] [name=institution]',node=>node.value='Institución escrita');
 await page.evaluate(()=>uiI18n.change('es'));assert.equal(await page.$eval('dialog[open] [name=institution]',node=>node.value),'Institución escrita');
 assert.equal(await page.$eval('.author-identity strong',node=>node.textContent),'Autora <original>');
 await page.$$eval('dialog[open] button',nodes=>nodes.find(node=>node.textContent==='Buscar en ROR').click());
 await page.waitForSelector('.ror-option');const rorCalls=calls.length;await page.evaluate(()=>uiI18n.change('en'));
 assert.equal(await page.$eval('.ror-option',node=>node.textContent),'Institución ROR original · 012345678');assert.equal(calls.length,rorCalls);
 assert.match(await page.$eval('.author-identity small',node=>node.textContent),/not authenticated/);
 await page.click('.ror-option');assert.equal(await page.$eval('dialog[open] [name=institution]',node=>node.value),'Institución ROR original');
 await page.$eval('dialog[open]',node=>node.close());
 versionsFail=true;await page.$eval('#versions-pagination button:last-child',node=>node.click());await page.waitForFunction(()=>document.querySelector('#versions-pagination button')?.textContent==='Retry versions');
 const failureCalls=calls.length;await page.evaluate(()=>uiI18n.change('es'));
 assert.match(await page.$eval('#versions-status',node=>node.textContent),/No se pudieron consultar/);assert.equal(calls.length,failureCalls);
 versionsFail=false;await page.click('#versions-pagination button');await page.waitForSelector('#versions-list li');
 assert.deepEqual(errors,[]);console.log('Public versions and relation/funding/creator editors: es/en, controls, original metadata, payload, retry and no locale requests PASS');
 }finally{await browser.close();await new Promise(r=>server.close(r));}})().catch(error=>{console.error(error);process.exitCode=1;server.close();});
