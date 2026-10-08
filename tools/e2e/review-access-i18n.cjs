#!/usr/bin/env node
// Isolated UI fixture; does not prove backend bearer-token authorization.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const puppeteer=require('../a11y/node_modules/puppeteer-core');
const root=path.resolve(__dirname,'../../src/main/resources/static');const calls=[];let revoked=false;
const server=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://fixture');
 if(url.pathname.startsWith('/api/v1/reviewer/')){
  calls.push({path:url.pathname,token:req.headers['x-review-token']});res.setHeader('Content-Type','application/json');
  if(revoked){res.writeHead(403);res.end('{}');return;}
  if(url.pathname.endsWith('/metadata')){res.end(JSON.stringify({title:'Título <original>',authors:['Autor original'],publisher:'Institución original',year:'2026',markdown:'# description original [data-i18n]'}));return;}
  res.end(JSON.stringify({files:[{path:'archivo original.ods',size:123,mediaType:'application/ods',sha256:'original-checksum'}],pages:2}));return;
 }
 const name=url.pathname.slice(1);if(!/^[\w.-]+$/.test(name)||!fs.existsSync(path.join(root,name))){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript; charset=utf-8':name.endsWith('.css')?'text/css; charset=utf-8':'text/html; charset=utf-8');res.end(fs.readFileSync(path.join(root,name)));
});
(async()=>{await new Promise(r=>server.listen(0,'127.0.0.1',r));const browser=await puppeteer.launch({executablePath:'/usr/bin/google-chrome',headless:true,args:['--no-sandbox']});try{
 const page=await browser.newPage(),errors=[];page.on('pageerror',error=>errors.push(error.message));await page.goto(`http://127.0.0.1:${server.address().port}/review-access.html#token=fixture-secret`);
 await page.waitForSelector('#review-content:not([hidden])');assert.equal(new URL(page.url()).hash,'');assert(calls.every(call=>call.token==='fixture-secret'));
 const count=calls.length;await page.select('[data-ui-locale]','en');assert.equal(await page.$eval('h1',node=>node.textContent),'Private deposit review');
 assert.equal(await page.$eval('#review-title',node=>node.textContent),'Título <original>');assert.equal(await page.$eval('#review-markdown',node=>node.textContent),'# description original [data-i18n]');
 assert.equal(await page.$eval('#review-files strong',node=>node.textContent),'archivo original.ods');assert.equal(calls.length,count);
 assert.equal(await page.$eval('#review-pagination span',node=>node.textContent),'Page 1 of 2');assert.match(await page.$eval('#review-files',node=>node.textContent),/SHA-256 at ingest: original-checksum/);
 revoked=true;await page.click('#review-pagination button:last-child');await page.waitForFunction(()=>document.querySelector('#review-status').textContent.includes('revoked'));
 assert.equal(await page.$eval('#review-files',node=>node.childElementCount),0);const failedCount=calls.length;await page.select('[data-ui-locale]','es');assert.match(await page.$eval('#review-status',node=>node.textContent),/revocado/);assert.equal(calls.length,failedCount);
 assert.equal(await page.evaluate(()=>Object.keys(localStorage).some(key=>String(localStorage[key]).includes('fixture-secret'))),false);
 await page.goto(`http://127.0.0.1:${server.address().port}/review-access.html`);await page.waitForFunction(()=>document.querySelector('#review-status').textContent.includes('Falta'));await page.select('[data-ui-locale]','en');assert.equal(await page.$eval('#review-status',node=>node.textContent),'The review link is missing.');
 assert.deepEqual(errors,[]);console.log('Temporary review es/en, original content, token removal, pagination failure and no locale requests PASS');
 }finally{await browser.close();await new Promise(r=>server.close(r));}})().catch(error=>{console.error(error);process.exitCode=1;server.close();});
