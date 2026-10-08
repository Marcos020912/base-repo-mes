#!/usr/bin/env node
/* Isolated transfer UI test: local HTTP only, no deployed data or credentials. */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const puppeteer = require('../a11y/node_modules/puppeteer-core');
const script = fs.readFileSync(path.resolve(__dirname, '../../src/main/resources/static/transfers.js'));
let closed = false;
let unauthorizedHeader = false;
const server = http.createServer((request, response) => {
  unauthorizedHeader ||= Boolean(request.headers.authorization);
  if (request.url === '/') {
    response.setHeader('Content-Type', 'text/html');
    response.end('<header class="page-header"></header><script src="/transfers.js"></script>');
  } else if (request.url === '/transfers.js') {
    response.setHeader('Content-Type', 'application/javascript'); response.end(script);
  } else if (request.url === '/slow') {
    response.setHeader('Content-Type', 'application/octet-stream');
    const timer = setInterval(() => response.write(Buffer.alloc(4096)), 50);
    response.on('close', () => {clearInterval(timer); closed = true;});
  } else if (request.url === '/file') {
    response.setHeader('Content-Length', '5'); response.end('datos');
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
    await page.click('.transfer-heading button');
    assert.equal(await page.$$eval('.transfer-item', items => items.length), 0);
    assert.deepEqual(errors, []);
    console.log('Transferencias OK: stream sin tamaño, cancelación HTTP, éxito, error y limpieza anónimos.');
  } finally {
    if (browser) await browser.close();
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
  }
})().catch(error => {console.error(error); process.exitCode=1;});
