#!/usr/bin/env node
/* Isolated transfer UI test: local HTTP only, no deployed data or credentials. */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const puppeteer = require('../a11y/node_modules/puppeteer-core');
const script = fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/static/transfers.js'));
let closed = false;
let uploadClosed = false;
let uploadStarted = false;
let uploaded = 0;
let unauthorizedHeader = false;
const server = http.createServer((request, response) => {
  unauthorizedHeader ||= Boolean(request.headers.authorization);
  if (request.url === '/') {
    response.setHeader('Content-Type', 'text/html; charset=utf-8');
    response.end('<header class="page-header"></header><script src="/transfers.js"></script>');
  } else if (request.url === '/transfers.js') {
    response.setHeader('Content-Type', 'application/javascript; charset=utf-8'); response.end(script);
  } else if (request.url === '/upload') {
    request.on('data', chunk => uploaded += chunk.length);
    request.on('end', () => { response.writeHead(201); response.end('{}'); });
  } else if (request.url === '/upload-error') {
    request.resume(); response.writeHead(400, {'Content-Type':'application/json'});
    response.end(JSON.stringify({detail:'Archivo incompatible con el tipo de dataset.'}));
  } else if (request.url === '/upload-slow') {
    uploadStarted = true; request.resume(); response.on('close', () => uploadClosed = true);
  } else if (request.url === '/slow') {
    response.setHeader('Content-Type', 'application/octet-stream');
    const timer = setInterval(() => response.write(Buffer.alloc(4096)), 50);
    response.on('close', () => {clearInterval(timer); closed = true;});
  } else if (request.url === '/file') {
    response.setHeader('Content-Length', '5'); response.end('datos');
  } else if (request.url === '/problem') {
    response.writeHead(409, {'Content-Type':'application/problem+json; charset=utf-8'});
    response.end(JSON.stringify({status:409, detail:'El archivo cambió. Actualiza la ficha antes de descargar.'}));
  } else if (request.url === '/error') {
    response.writeHead(403); response.end('Forbidden');
  } else {response.writeHead(404); response.end();}
});
(async () => {
  let browser;
  try {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    browser = await puppeteer.launch({executablePath:process.env.CHROME_BIN || '/usr/bin/google-chrome',
      headless:true, args:['--no-sandbox', '--disable-dev-shm-usage']});
    const page = await browser.newPage();
    const errors = []; page.on('pageerror', error => errors.push(error.message));
    await page.goto('http://127.0.0.1:' + server.address().port);
    await page.evaluate(() => {
      window.downloadResult = null;
      transfers.download('/slow', 'large.zip').then(() => window.downloadResult='success',
        error => window.downloadResult=error.name);
    });
    await page.waitForFunction(() => document.querySelector('.transfer-item span').textContent.includes('bytes'));
    assert.equal(await page.$eval('.transfer-item progress', el => el.hasAttribute('value')), false,
      'Sin Content-Length el progreso debe ser indeterminado.');
    await page.click('.transfer-toggle');
    assert.equal(await page.evaluate(() => document.activeElement.id), 'transfer-panel');
    await page.keyboard.press('Escape');
    assert.equal(await page.$eval('#transfer-panel', el => el.hidden), true);
    assert.equal(await page.$eval('.transfer-toggle', el => el.getAttribute('aria-expanded')), 'false');
    assert.equal(await page.evaluate(() => document.activeElement.className), 'transfer-toggle');
    await page.click('.transfer-toggle');
    await page.click('.transfer-item button');
    await page.waitForFunction(() => window.downloadResult === 'AbortError');
    await page.waitForFunction(() => document.querySelector('.transfer-item span').textContent === 'Descarga cancelada');
    for (let i=0; i<20 && !closed; i++) await new Promise(resolve => setTimeout(resolve, 50));
    assert(closed, 'Cancelar no cerró la conexión HTTP del servidor.');
    assert.equal(await page.$eval('.transfer-item progress', el => el.hidden), true);
    await page.evaluate(() => transfers.download('/file', 'file.txt'));
    assert.equal(await page.$eval('.transfer-item progress', el => el.value), 100);
    assert.equal(await page.$eval('.transfer-item span', el => el.textContent), 'Descarga preparada');
    const failure = await page.evaluate(async () => {
      try {await transfers.download('/error', 'private.csv'); return null;}
      catch(error) {return error.message;}
    });
    assert.equal(failure, 'No se pudo descargar el archivo.');
    assert.equal(await page.$eval('.transfer-item span', el => el.textContent), 'Error en la descarga');
    assert.equal(unauthorizedHeader, false, 'La página pública envió Authorization.');
    await page.click('.transfer-heading button:not(.transfer-close)');
    assert.equal(await page.$$eval('.transfer-item', items => items.length), 0);
    await page.evaluate(() => transfers.upload('/upload', new File(['nombre,valor\nuno,1\n'], 'datos.csv')));
    assert(uploaded > 18, 'No se recibió el cuerpo multipart de la subida.');
    assert.equal(await page.$eval('.transfer-item span', el => el.textContent), 'Subida completada');
    const uploadFailure = await page.evaluate(async () => {
      try { await transfers.upload('/upload-error', new File(['x'], 'wrong.exe')); return null; }
      catch (error) { return error.message; }
    });
    assert.equal(uploadFailure, 'Archivo incompatible con el tipo de dataset.');
    assert.equal(await page.$eval('.transfer-item span', el => el.textContent), 'Error en la subida');
    await page.evaluate(() => {
      window.uploadResult = null;
      transfers.upload('/upload-slow', new File([new Uint8Array(8 * 1024 * 1024)], 'slow.csv'))
        .then(() => window.uploadResult = 'success', error => window.uploadResult = error.message);
    });
    await page.waitForFunction(() => document.querySelector('.transfer-item strong').textContent.includes('slow.csv'));
    for (let i=0; i<40 && !uploadStarted; i++) await new Promise(resolve => setTimeout(resolve, 50));
    assert(uploadStarted, 'La subida no llegó al servidor antes de probar cancelación.');
    await page.waitForFunction(() => document.querySelector('.transfer-item span').textContent.includes('esperando confirmación del servidor'), {timeout:5000}).catch(async error => { throw new Error(error.message + ': ' + await page.$eval('.transfer-item', el => el.textContent)); });
    assert.equal(await page.$eval('.transfer-item progress', el => el.hasAttribute('value')), false,
      'La espera del servidor no debe mostrar 100 % como operación completada.');
    assert.equal(await page.$eval('.transfer-item button', el => el.hidden), false,
      'La petición debe poder cancelarse mientras espera al servidor.');
    await page.click('.transfer-item button');
    await page.waitForFunction(() => window.uploadResult === 'Subida cancelada.');
    for (let i=0; i<20 && !uploadClosed; i++) await new Promise(resolve => setTimeout(resolve, 50));
    assert(uploadClosed, 'Cancelar subida no cerró la conexión HTTP.');
    assert.equal(await page.$eval('.transfer-item span', el => el.textContent), 'Subida cancelada');
    assert.equal(await page.$eval('.transfer-item button', el => el.hidden), true);
    await page.click('.transfer-close');
    assert.equal(await page.$eval('#transfer-panel', el => el.hidden), true);
    assert.equal(await page.evaluate(() => document.activeElement.className), 'transfer-toggle');
    const problem = await page.evaluate(async () => {
      try {await transfers.download('/problem', 'changed.csv'); return null;}
      catch (error) {return error.message;}
    });
    assert.equal(problem, 'El archivo cambió. Actualiza la ficha antes de descargar.');
    const privateProblem = await page.evaluate(async () => {
      try {await transfers.download('/problem', 'changed.csv', 'private', {errorMessage:'No se pudo descargar el archivo.'}); return null;}
      catch (error) {return error.message;}
    });
    assert.equal(privateProblem, 'No se pudo descargar el archivo.');
    const external = await page.evaluate(async () => {
      try { await transfers.download('https://example.invalid/file', 'file.csv', 'file', {headers:{'X-Review-Token':'test-only'}}); return null; }
      catch (error) { return error.message; }
    });
    assert.equal(external, 'Solo se permiten descargas desde este servidor.');
    assert.deepEqual(errors, []);
    console.log('Transferencias OK: stream sin tamaño, cancelación HTTP, éxito, error y limpieza anónimos; subida multipart, rechazo y cancelación HTTP.');
  } finally {
    if (browser) await browser.close();
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
  }
})().catch(error => {console.error(error); process.exitCode=1;});
