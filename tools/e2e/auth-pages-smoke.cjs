#!/usr/bin/env node
/* Local UI regression only; no mail, database or live server. */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const puppeteer = require('../a11y/node_modules/puppeteer-core');
const root = path.resolve(__dirname, '../../src/main/resources/static');
const allowed = new Set(['login.html','register.html','auth.js','login.js','register.js','styles.css','login.css']);
let requests = 0;
const server = http.createServer((request,response) => {
  if (request.url === '/api/v1/auth/login') {
    requests++;
    request.resume();
    response.writeHead(401, {'Content-Type':'application/json; charset=utf-8'});
    response.end(JSON.stringify({message:'Credenciales no válidas.'})); return;
  }
  const file = request.url.slice(1);
  if (!allowed.has(file)) {response.writeHead(404);response.end();return;}
  response.setHeader('Content-Type', (file.endsWith('.js') ? 'application/javascript' :
    file.endsWith('.css') ? 'text/css' : 'text/html') + '; charset=utf-8');
  response.end(fs.readFileSync(path.join(root,file)));
});
(async()=>{
  let browser;
  try {
    await new Promise(resolve => server.listen(0,'127.0.0.1',resolve));
    const base = 'http://127.0.0.1:' + server.address().port;
    browser = await puppeteer.launch({executablePath:process.env.CHROME_BIN || '/usr/bin/google-chrome',
      headless:true,args:['--no-sandbox','--disable-dev-shm-usage']});
    const page = await browser.newPage();
    const errors=[]; page.on('pageerror',error=>errors.push(error.message));
    await page.goto(base+'/login.html');
    await page.evaluate(()=>localStorage.setItem('base-repo-token','invalid-local-fixture'));
    for (const name of ['login','register']) {
      await page.goto(base+'/'+name+'.html',{waitUntil:'load'});
      await page.waitForSelector('#'+name+'-form');
      assert(new URL(page.url()).pathname === '/'+name+'.html','Token viejo redirigió '+name);
    }
    const password = crypto.randomBytes(12).toString('hex');
    await page.type('input[name=username]','fixture');
    await page.type('input[name=email]','fixture@example.invalid');
    await page.type('input[name=password]',password);
    await page.type('input[name=confirmation]',password+'different');
    await page.click('#register-form button');
    await page.waitForFunction(()=>document.querySelector('#register-message').textContent==='Las contraseñas no coinciden.');
    assert.equal(requests,0,'Se envió una solicitud con confirmación incorrecta.');
    await page.goto(base+'/login.html');
    await page.type('input[name=username]','fixture');
    await page.type('input[name=password]',password);
    await page.click('#login-form button');
    await page.waitForFunction(()=>document.querySelector('#login-message').textContent==='Credenciales no válidas.');
    assert.equal(requests,1);
    assert.equal(new URL(page.url()).pathname,'/login.html');
    assert.deepEqual(errors,[]);
    console.log('Auth UI OK: login/registro accesibles con token viejo, confirmación y error legible.');
  } finally {
    if(browser) await browser.close();
    server.closeAllConnections();
    await new Promise(resolve=>server.close(resolve));
  }
})().catch(error=>{console.error(error);process.exitCode=1;});
