#!/usr/bin/env node
// Isolated UI fixtures, not a backend authorization test.
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const puppeteer=require('../a11y/node_modules/puppeteer-core');
const root=path.resolve(__dirname,'../../src/main/resources/static');
let calls=0;
const server=http.createServer((req,res)=>{
 if(req.url==='/api/v1/users'){calls++;res.setHeader('Content-Type','application/json');res.end(JSON.stringify([{id:42,username:'Autor <original>',role:'USER',enabled:true}]));return;}
 const name=req.url.split('?')[0].slice(1);
 if(!/^[\w.-]+$/.test(name)||!fs.existsSync(path.join(root,name))){res.writeHead(404);res.end();return;}
 res.setHeader('Content-Type',name.endsWith('.js')?'application/javascript':name.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(path.join(root,name)));
});
(async()=>{await new Promise(r=>server.listen(0,'127.0.0.1',r));const browser=await puppeteer.launch({executablePath:'/usr/bin/google-chrome',headless:true,args:['--no-sandbox']});try{
 const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));await page.evaluateOnNewDocument(()=>{localStorage.setItem('base-repo-token','fixture');localStorage.setItem('base-repo-user',JSON.stringify({username:'admin',role:'ADMINISTRATOR'}));});
 await page.goto(`http://127.0.0.1:${server.address().port}/users.html`);await page.waitForSelector('.edit-user');await page.select('[data-ui-locale]','en');assert.equal(await page.$eval('h1',n=>n.textContent),'Users');assert.equal(await page.$eval('option[value=USER]',n=>n.textContent),'User');assert.equal(await page.$eval('.table-row strong',n=>n.textContent),'Autor <original>');
 await page.click('.edit-user');await page.type('[name=username]',' unchanged');await page.select('[data-ui-locale]','es');assert.match(await page.$eval('[name=username]',n=>n.value),/unchanged$/);assert.equal(await page.$eval('#user-modal-title',n=>n.textContent),'Actualizar usuario');await page.$eval('#user-modal',n=>n.close());
 await page.click('.delete-user');await page.evaluate(()=>uiI18n.change('en'));assert.equal(await page.$eval('#delete-text',n=>n.textContent),'Delete Autor <original>? This action cannot be undone.');assert.equal(calls,1);
 assert(await page.evaluate(()=>{const n=document.createElement('button');document.body.append(n);uiI18n.attribute(n,'aria-label','users.confirmDelete',{username:'<original>'});uiI18n.change('es');const safe=n.getAttribute('aria-label')==='Eliminar a <original>? Esta acción no se puede deshacer.';let blocked=false;try{uiI18n.attribute(n,'href','users.delete');}catch{blocked=true;}n.remove();return safe&&blocked;}));assert.deepEqual(errors,[]);console.log('Users translation, parameters, author preservation and no extra requests: PASS');
 }finally{await browser.close();await new Promise(r=>server.close(r));}})().catch(e=>{console.error(e);process.exitCode=1;server.close();});
