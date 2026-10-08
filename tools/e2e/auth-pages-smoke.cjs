#!/usr/bin/env node
/* Local UI regression only; no mail, database or live server. */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const puppeteer = require('../a11y/node_modules/puppeteer-core');
const root = path.resolve(__dirname, '../../src/main/resources/static');
const allowed = new Set(['account.html','account.js','login.html','register.html','auth.js','login.js','register.js','verify.html','verify.js','styles.css','login.css']);
let requests = 0;
let resends = 0;
let registrations = 0;
let passwordChanges = 0;
const server = http.createServer((request,response) => {
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
      response.writeHead(attempt===1 ? 401 : 502, {'Content-Type':attempt===1 ? 'application/json; charset=utf-8' : 'text/html'});
      response.end(attempt===1 ? JSON.stringify({message:'Credenciales no válidas.'}) : '<h1>Proxy unavailable</h1>');
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
    assert.equal(await page.$eval('#login-form button',el=>el.disabled),true);
    await page.evaluate(()=>document.querySelector('#login-form').requestSubmit());
    await page.waitForFunction(()=>document.querySelector('#login-message').textContent==='Credenciales no válidas.');
    assert.equal(requests,1);
    assert.equal(new URL(page.url()).pathname,'/login.html');
    await page.click('#login-form button');
    await page.waitForFunction(()=>document.querySelector('#login-message').textContent==='No se pudo iniciar sesión.');
    assert.equal(requests,2,'Login duplicó solicitudes pendientes.');
    await page.goto(base+'/register.html');
    for (const [name,value] of Object.entries({username:'fixture',email:'fixture@example.invalid',password,confirmation:password}))
      await page.type(`input[name=${name}]`,value);
    await page.click('#register-form button');
    assert.equal(await page.$eval('#register-form button',el=>el.disabled),true);
    await page.evaluate(()=>document.querySelector('#register-form').requestSubmit());
    await page.waitForFunction(()=>location.pathname==='/verify.html');
    assert.equal(registrations,1,'Registro duplicó solicitudes pendientes.');
    assert.equal(new URL(page.url()).searchParams.get('mailPending'),'1');
    assert((await page.$eval('#verify-message',el=>el.textContent)).includes('cuenta fue creada'));

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
    await page.waitForFunction(()=>document.querySelector('#verify-message').textContent==='Correo temporalmente no disponible.');
    assert.equal(resends,1,'Se duplicó el reenvío pendiente.');
    await page.click('#resend');
    await page.waitForFunction(()=>document.querySelector('#verify-message').classList.contains('success'));
    assert.equal(resends,2);
    assert.equal(await page.$eval('#verify-message',el=>el.classList.contains('error')),false);
    await page.type('input[name=code]','123456');
    await page.click('#verify-form button');
    await page.waitForFunction(()=>document.querySelector('#verify-message')?.textContent==='Correo verificado. Redirigiendo…');
    assert.equal(await page.$eval('#resend',el=>el.disabled),true);
    await page.waitForFunction(()=>location.pathname==='/login.html');
    await page.goto(base+'/account.html');
    for (const [name,value] of Object.entries({currentPassword:password,newPassword:password+'new',confirmation:password+'new'}))
      await page.type(`input[name=${name}]`,value);
    await page.click('#password-form button');
    assert.equal(await page.$eval('#password-form button',el=>el.disabled),true);
    await page.evaluate(()=>document.querySelector('#password-form').requestSubmit());
    await page.waitForFunction(()=>!document.querySelector('#password-form button').disabled);
    assert.equal(passwordChanges,1);
    assert((await page.content()).includes('Contraseña actual incorrecta.'));
    await page.click('#password-form button');
    await page.waitForFunction(()=>document.body.textContent.includes('Contraseña actualizada.'));
    await page.evaluate(()=>document.querySelector('#password-form').requestSubmit());
    assert.equal(await page.$eval('#password-form button',el=>el.disabled),true);
    await page.waitForFunction(()=>location.pathname==='/login.html');
    assert.equal(passwordChanges,2,'Cambio de contraseña duplicó solicitudes.');
    assert.equal(await page.evaluate(()=>localStorage.getItem('base-repo-token')),null);
    assert.deepEqual(errors,[]);
    console.log('Auth UI OK: acceso, correo válido, reenvío/verificación y cambio de contraseña sin duplicados.');
  } finally {
    if(browser) await browser.close();
    server.closeAllConnections();
    await new Promise(resolve=>server.close(resolve));
  }
})().catch(error=>{console.error(error);process.exitCode=1;});
