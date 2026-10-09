#!/usr/bin/env node
/* Local UI regression only; no mail, database or live server. */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const puppeteer = require('../a11y/node_modules/puppeteer-core');
const axe=require('../a11y/node_modules/axe-core');
const root = path.resolve(__dirname, '../../src/main/resources/static');
const allowed = new Set(['account.html','account.js','login.html','register.html','auth.js','login.js','register.js','verify.html','verify.js','ui-locales.js','ui-i18n.js','styles.css','login.css']);
let requests = 0;
let resends = 0;
let registrations = 0;
let passwordChanges = 0;
const server = http.createServer((request,response) => {
  if (request.url === '/index.html') {
    response.writeHead(200, {'Content-Type':'text/html; charset=utf-8'});
    response.end('<!doctype html><title>Authenticated fixture</title>'); return;
  }
  if (request.url === '/api/v1/auth/change-password') {
    passwordChanges++; request.resume();
    const attempt = passwordChanges;
    setTimeout(() => {
      response.writeHead(attempt === 1 ? 400 : 200, {'Content-Type':'application/json'});
      response.end(JSON.stringify(attempt === 1 ? {message:'Contraseña actual incorrecta.'} : {}));
    },200); return;
  }
  if (request.url === '/api/v1/auth/login') {
    requests++;
    request.resume();
    const attempt=requests;
    setTimeout(() => {
      response.writeHead(attempt===1 ? 401 : attempt===2 ? 502 : 200, {'Content-Type':attempt===2 ? 'text/html' : 'application/json; charset=utf-8'});
      response.end(attempt===1 ? JSON.stringify({message:'Credenciales no válidas.'}) :
        attempt===2 ? '<h1>Proxy unavailable</h1>' :
        JSON.stringify(attempt===3 ? {} : {token:'local-success-fixture',user:{username:'fixture',role:'USER'}}));
    },200); return;
  }
  if (request.url === '/api/v1/auth/register') {
    registrations++; request.resume();
    setTimeout(() => {
      response.writeHead(503, {'Content-Type':'application/json; charset=utf-8'});
      response.end(JSON.stringify({code:'VERIFICATION_MAIL_UNAVAILABLE',message:'La cuenta se creó, pero no pudimos enviar el código.'}));
    },200); return;
  }
  if (request.url === '/api/v1/auth/resend-verification') {
    resends++; request.resume();
    const failure = resends === 1;
    setTimeout(() => {
      response.writeHead(failure ? 503 : 200, {'Content-Type':'application/json; charset=utf-8'});
      response.end(JSON.stringify(failure ? {message:'Correo temporalmente no disponible.'} : {}));
    }, 200); return;
  }
  if (request.url === '/api/v1/auth/verify') {
    request.resume(); response.writeHead(200, {'Content-Type':'application/json'}); response.end('{}'); return;
  }
  const file = new URL(request.url, 'http://localhost').pathname.slice(1);
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
    await page.type('input[name=username]','kept-local-input');
    await page.evaluate(()=>{const author=document.createElement('p');author.id='author-content-fixture';author.dataset.i18n='untrusted-key-not-in-catalogue';author.textContent='Author content must remain unchanged';document.querySelector('main').append(author);});
    await page.select('[data-ui-locale]','en');
    assert.equal(await page.$eval('h1',node=>node.textContent),'Sign in');
    assert.equal(await page.$eval('#author-content-fixture',node=>node.textContent),'Author content must remain unchanged');
    assert.equal(await page.$eval('input[name=username]',node=>node.value),'kept-local-input');
    assert.equal(await page.$eval('html',node=>node.lang),'en');
    await page.setViewport({width:320,height:700});await page.evaluate(axe.source);
    const accessibility=await page.evaluate(async()=>axe.run({runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa']}}));
    assert.equal(accessibility.violations.length,0,'Login English at320px has axe violations: '+accessibility.violations.map(item=>item.id).join(','));
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>document.documentElement.clientWidth+1),false);
    await page.setViewport({width:1280,height:800});
    await page.goto(base+'/verify.html?mailPending=1');
    assert.equal(await page.$eval('h1',node=>node.textContent),'Verify your email');
    assert((await page.$eval('.toast:last-child .toast-text',node=>node.textContent)).includes('could not be sent'));
    await page.select('[data-ui-locale]','es');
    assert((await page.$eval('.toast:last-child .toast-text',node=>node.textContent)).includes('correo no salió'));
    assert.equal(requests+resends+registrations,0,'Cambiar idioma envió solicitudes de autenticación.');
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
    await page.click('#register-form button[type=submit]');
    await page.waitForFunction(()=>document.querySelector('.toast:last-child .toast-text')?.textContent==='Las contraseñas no coinciden.');
    assert.equal(requests,0,'Se envió una solicitud con confirmación incorrecta.');
    await page.goto(base+'/login.html');
    await page.type('input[name=username]','fixture');
    await page.type('input[name=password]',password);
    await page.click('#login-form button[type=submit]');
    assert.equal(await page.$eval('#login-form button[type=submit]',el=>el.disabled),true);
    await page.evaluate(()=>document.querySelector('#login-form').requestSubmit());
    await page.waitForFunction(()=>document.querySelector('.toast:last-child .toast-text')?.textContent==='Credenciales no válidas.');
    assert.equal(requests,1);
    assert.equal(new URL(page.url()).pathname,'/login.html');
    await page.click('#login-form button[type=submit]');
    await page.waitForFunction(()=>document.querySelector('.toast:last-child .toast-text')?.textContent==='No se pudo iniciar sesión.');
    assert.equal(requests,2,'Login duplicó solicitudes pendientes.');
    await page.select('[data-ui-locale]','en');assert.equal(await page.$eval('.toast:last-child .toast-text',node=>node.textContent),'Unable to sign in.');
    assert.equal(await page.$eval('input[name=password]',node=>node.value),password);
    await page.select('[data-ui-locale]','es');
    await page.click('#login-form button[type=submit]');
    await page.waitForFunction(()=>document.querySelector('.toast:last-child .toast-text')?.textContent.includes('respuesta del servidor no es válida'));
    assert.equal(new URL(page.url()).pathname,'/login.html');
    assert.equal(await page.evaluate(()=>localStorage.getItem('base-repo-token')),'invalid-local-fixture');
    assert.equal(await page.evaluate(()=>localStorage.getItem('base-repo-user')),null);
    await page.click('#login-form button[type=submit]');
    await page.waitForFunction(()=>location.pathname==='/index.html');
    assert.equal(await page.evaluate(()=>localStorage.getItem('base-repo-token')),'local-success-fixture');
    assert.equal(await page.evaluate(()=>JSON.parse(localStorage.getItem('base-repo-user')).username),'fixture');
    assert.equal(requests,4);

    await page.goto(base+'/register.html');
    for (const [name,value] of Object.entries({username:'fixture',email:'fixture@example.invalid',password,confirmation:password}))
      await page.type(`input[name=${name}]`,value);
    await page.click('#register-form button[type=submit]');
    assert.equal(await page.$eval('#register-form button[type=submit]',el=>el.disabled),true);
    await page.evaluate(()=>document.querySelector('#register-form').requestSubmit());
    await page.waitForFunction(()=>location.pathname==='/verify.html');
    assert.equal(registrations,1,'Registro duplicó solicitudes pendientes.');
    assert.equal(new URL(page.url()).searchParams.get('mailPending'),'1');
    assert((await page.$eval('.toast:last-child .toast-text',el=>el.textContent)).includes('cuenta fue creada'));

    assert.equal(await page.$eval('input[name=email]', el=>el.value),'fixture@example.invalid');
    for (const invalid of ['', 'not-an-email']) {
      await page.$eval('input[name=email]',(el,value)=>el.value=value,invalid);
      await page.click('#resend');
      assert.equal(resends,0,'Reenvío envió un correo inválido.');
      assert.equal(await page.$eval('input[name=email]',el=>el.validity.valid),false);
    }
    await page.$eval('input[name=email]',el=>el.value='fixture@example.invalid');
    await page.click('#resend');
    assert.equal(await page.$eval('#verify-form button',el=>el.disabled),true);
    await page.evaluate(()=>document.querySelector('#resend').click());
    await page.waitForFunction(()=>document.querySelector('.toast:last-child .toast-text')?.textContent==='Correo temporalmente no disponible.');
    assert.equal(resends,1,'Se duplicó el reenvío pendiente.');
    await page.click('#resend');
    await page.waitForFunction(()=>Boolean(document.querySelector('.toast.success')));
    assert.equal(resends,2);
    assert.equal(await page.$eval('#verify-message',el=>el.classList.contains('error')),false);
    await page.type('input[name=code]','123456');
    await page.click('#verify-form button');
    await page.waitForFunction(()=>document.querySelector('.toast:last-child .toast-text')?.textContent==='Correo verificado. Redirigiendo…');
    assert.equal(await page.$eval('#resend',el=>el.disabled),true);
    await page.waitForFunction(()=>location.pathname==='/login.html');
    await page.goto(base+'/account.html');
    for (const [name,value] of Object.entries({currentPassword:password,newPassword:password+'new',confirmation:password+'new'}))
      await page.type(`input[name=${name}]`,value);
    await page.select('[data-ui-locale]','en');
    assert.equal(await page.$eval('h1',node=>node.textContent),'My account');
    assert.equal(await page.$eval('.sidebar a[href="account.html"]',node=>node.textContent),'◉ My account');
    assert.equal(await page.$eval('input[name=currentPassword]',node=>node.value),password);
    await page.setViewport({width:320,height:700});await page.evaluate(axe.source);
    const accountAccessibility=await page.evaluate(async()=>axe.run({runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa','wcag22aa']}}));
    assert.equal(accountAccessibility.violations.length,0,'Account English at320px axe violations: '+accountAccessibility.violations.map(item=>item.id).join(','));
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>document.documentElement.clientWidth+1),false);
    await page.setViewport({width:1280,height:800});await page.select('[data-ui-locale]','es');
    await page.click('#password-form button[type=submit]');
    assert.equal(await page.$eval('#password-form button[type=submit]',el=>el.disabled),true);
    await page.evaluate(()=>document.querySelector('#password-form').requestSubmit());
    await page.waitForFunction(()=>!document.querySelector('#password-form button[type=submit]').disabled);
    assert.equal(passwordChanges,1);
    assert((await page.content()).includes('Contraseña actual incorrecta.'));
    await page.click('#password-form button[type=submit]');
    await page.waitForFunction(()=>document.body.textContent.includes('Contraseña actualizada.'));
    await page.evaluate(()=>document.querySelector('#password-form').requestSubmit());
    assert.equal(await page.$eval('#password-form button[type=submit]',el=>el.disabled),true);
    await page.waitForFunction(()=>location.pathname==='/login.html');
    assert.equal(passwordChanges,2,'Cambio de contraseña duplicó solicitudes.');
    assert.equal(await page.evaluate(()=>localStorage.getItem('base-repo-token')),null);
    const restricted=await browser.newPage();restricted.on('pageerror',error=>errors.push(error.message));
    await restricted.setRequestInterception(true);restricted.on('request',request=>{if(new URL(request.url()).pathname==='/api/v1/auth/login')request.respond({status:403,contentType:'application/json',body:JSON.stringify({code:'ACCOUNT_RESTRICTED'})});else request.continue();});
    await restricted.goto(base+'/login.html');await restricted.type('input[name=username]','fixture');await restricted.type('input[name=password]',password);await restricted.click('#login-form button[type=submit]');
    await restricted.waitForSelector('#login-message a[href="mailto:soporte@mes.gob.cu"]');await restricted.select('[data-ui-locale]','en');
    assert((await restricted.$eval('.toast:last-child .toast-text',node=>node.textContent)).includes('Your account has been restricted.'));
    assert.equal(await restricted.$eval('#login-message a',node=>node.getAttribute('href')),'mailto:soporte@mes.gob.cu');await restricted.close();
    const blocked=await browser.newPage();blocked.on('pageerror',error=>errors.push(error.message));
    await blocked.evaluateOnNewDocument(()=>Object.defineProperty(window,'localStorage',{get(){throw new DOMException('Storage disabled','SecurityError');}}));
    await blocked.goto(base+'/login.html');await blocked.type('input[name=username]','kept-without-storage');
    await blocked.select('[data-ui-locale]','en');assert.equal(await blocked.$eval('h1',node=>node.textContent),'Sign in');
    assert.equal(await blocked.$eval('input[name=username]',node=>node.value),'kept-without-storage');await blocked.close();
    assert.deepEqual(errors,[]);
    console.log('Auth UI OK: acceso, correo válido, reenvío/verificación y cambio de contraseña sin duplicados.');
  } finally {
    if(browser) await browser.close();
    server.closeAllConnections();
    await new Promise(resolve=>server.close(resolve));
  }
})().catch(error=>{console.error(error);process.exitCode=1;});
