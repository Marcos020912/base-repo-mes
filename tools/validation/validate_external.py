#!/usr/bin/env python3
"""Opt-in read-only deployment checks. Never sends credentials/mail/usage events."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import smtplib
import ssl
import socket
import hashlib
import base64
import urllib.error
import urllib.parse
import urllib.request

MAX_BODY = 2 * 1024 * 1024

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

class Validator:
    def __init__(self, base, timeout=10, ca_file=None):
        url=urllib.parse.urlsplit(base)
        if url.scheme != 'https' or not url.hostname or url.username or url.password or url.query or url.fragment or url.path not in ('','/'):
            raise ValueError('Use un origen HTTPS sin credenciales, ruta, parámetros o fragmento.')
        if not 1 <= timeout <= 60: raise ValueError('Timeout debe estar entre 1 y 60 segundos.')
        self.base=base.rstrip('/'); self.timeout=timeout
        self.context=ssl.create_default_context(cafile=ca_file)
        self.opener=urllib.request.build_opener(NoRedirect(),urllib.request.HTTPSHandler(context=self.context))
        self.checks=[]

    def request(self,url,method='GET',headers=None):
        req=urllib.request.Request(url,method=method,headers={'User-Agent':'RedUniv-validation-bot/1.0',**(headers or {})})
        try: response=self.opener.open(req,timeout=self.timeout)
        except urllib.error.HTTPError as error: response=error
        with response:
            body=response.read(MAX_BODY+1)
            if len(body)>MAX_BODY: raise ValueError('Respuesta excede límite seguro.')
            return response.status,response.headers,body

    def check(self,name,callback):
        try:
            state,detail=callback();self.checks.append({'check':name,'status':state,'detail':detail})
        except Exception as error:
            # No raw response, exception message, token, cookie or credentials in evidence.
            reason=getattr(error,'reason',error)
            category='TLS_CERTIFICATE_OR_HOSTNAME' if isinstance(reason,ssl.SSLError) else 'DNS' if isinstance(reason,socket.gaierror) else 'TIMEOUT' if isinstance(reason,TimeoutError) else type(reason).__name__
            self.checks.append({'check':name,'status':'FAIL','detail':category})

    def web(self):
        def login():
            code,headers,body=self.request(self.base+'/login.html')
            signature=b'login-form' in body and b'login.js' in body
            ok=code==200 and 'text/html' in headers.get('Content-Type','') and b'<html' in body.lower() and signature
            return ('PASS' if ok else 'FAIL',{'httpStatus':code,'tlsVerified':True,'redirectsFollowed':False,'applicationSignature':signature})
        def cors():
            code,headers,_=self.request(self.base+'/api/v1/auth/register','OPTIONS',{'Origin':self.base,'Access-Control-Request-Method':'POST','Access-Control-Request-Headers':'content-type'})
            if 200<=code<300 and not headers.get('Access-Control-Allow-Origin'):
                return 'INCONCLUSIVE',{'httpStatus':code,'note':'Mismo origen puede omitir cabeceras CORS; comprobar registro real en navegador autorizado. No demuestra un fallo por sí solo.'}
            ok=200<=code<300 and headers.get('Access-Control-Allow-Origin')==self.base and 'POST' in headers.get('Access-Control-Allow-Methods','')
            return ('PASS' if ok else 'FAIL',{'httpStatus':code,'originMatches':headers.get('Access-Control-Allow-Origin')==self.base})
        self.check('public_https_login',login);self.check('registration_cors_preflight',cors)

    def dataset(self,identifier,doi=None):
        if not re.fullmatch(r'[A-Za-z0-9_-]{1,255}',identifier): raise ValueError('Identificador de dataset no válido.')
        def public():
            code,headers,body=self.request(self.base+'/api/v1/public/resources/'+identifier)
            if code!=200:return 'FAIL',{'httpStatus':code}
            data=json.loads(body);ok=data.get('id')==identifier and (not doi or data.get('doi','').lower()==doi.lower())
            return ('PASS' if ok else 'FAIL',{'httpStatus':code,'doiMatches':not doi or data.get('doi','').lower()==doi.lower()})
        def landing():
            code,headers,body=self.request(self.base+'/datasets/'+identifier)
            return ('PASS' if code==200 and 'text/html' in headers.get('Content-Type','') else 'FAIL',{'httpStatus':code})
        self.check('public_dataset_metadata',public);self.check('permanent_landing',landing)
        if doi:
            if not re.fullmatch(r'10\.\d{4,9}/[^\s?#]{1,220}',doi):raise ValueError('DOI no válido.')
            def registered():
                code,_,body=self.request('https://api.datacite.org/dois/'+urllib.parse.quote(doi,safe='/'))
                if code!=200:return 'FAIL',{'httpStatus':code}
                data=json.loads(body).get('data',{});a=data.get('attributes',{});expected=self.base+'/datasets/'+identifier
                ok=data.get('id','').lower()==doi.lower() and a.get('state')=='findable' and a.get('url')==expected
                months=a.get('viewsOverTime',[]);downloads=a.get('downloadsOverTime',[])
                return ('PASS' if ok else 'FAIL',{'httpStatus':code,'state':a.get('state'),'landingMatches':a.get('url')==expected,'viewMonths':len(months) if isinstance(months,list) else None,'downloadMonths':len(downloads) if isinstance(downloads,list) else None})
            self.check('datacite_production_doi',registered)

    def tracker_library(self):
        def integrity():
            code,_,body=self.request('https://cdn.jsdelivr.net/npm/@datacite/datacite-tracker@0.0.5/dist/datacite-tracker.min.js')
            expected='DA254JbapivsjrXjoPMYQ6NY4y00XB/8MfuyZeQX8zc93wiapXSBTGBzR0xjTv9r'
            matches=base64.b64encode(hashlib.sha384(body).digest()).decode()==expected
            return ('PASS' if code==200 and matches else 'FAIL',{'httpStatus':code,'integrityMatches':matches,'version':'0.0.5'})
        self.check('datacite_tracker_library_integrity',integrity)

    def tracker(self,repository_id):
        if not re.fullmatch(r'da-[A-Za-z0-9_-]{1,100}',repository_id):raise ValueError('Identificador de estadísticas no válido.')
        def receipt():
            code,_,body=self.request('https://analytics.datacite.org/api/check/'+repository_id)
            # Receipt endpoint response proves connectivity, not provenance of an event/month.
            return ('INCONCLUSIVE' if code==200 else 'FAIL',{'httpStatus':code,'note':'Verificar recepción fechada del evento aprobado y el informe del mes siguiente; HTTP200 solo no lo demuestra.'})
        self.check('datacite_tracker_receipt',receipt)

    def smtp(self,host,port,mode):
        if mode not in ('tls','starttls'):raise ValueError('Modo SMTP no válido.')
        if not re.fullmatch(r'[A-Za-z0-9.-]{1,253}',host) or not 1<=port<=65535:raise ValueError('SMTP host/puerto no válidos.')
        def handshake():
            cls=smtplib.SMTP_SSL if mode=='tls' else smtplib.SMTP
            kwargs={'host':host,'port':port,'timeout':self.timeout}
            if mode=='tls':kwargs['context']=self.context
            with cls(**kwargs) as smtp:
                code,_=smtp.ehlo()
                if mode=='starttls':smtp.starttls(context=self.context);code,_=smtp.ehlo()
                return ('PASS' if 200<=code<300 else 'FAIL',{'tlsVerified':True,'protocol':smtp.sock.version(),'mailSent':False,'authenticationTested':False})
        self.check('smtp_tls_handshake',handshake)

    def report(self):
        return {'schema':'reduniv.external-validation.v1','generatedAt':dt.datetime.now(dt.timezone.utc).isoformat(),'target':self.base,'readOnly':True,'certified':False,'scope':[c['check'] for c in self.checks],'result':'FAIL' if any(c['status']=='FAIL' for c in self.checks) else 'INCONCLUSIVE' if any(c['status']=='INCONCLUSIVE' for c in self.checks) else 'PASS','checks':self.checks,'limitations':['No crea usuarios/DOI ni envía correos o eventos de uso.','La conectividad SMTP no demuestra entrega de correo.','Una ejecución no sustituye auditoría COUNTER, revisión WCAG manual ni aprobación institucional.']}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url',required=True);parser.add_argument('--dataset-id');parser.add_argument('--doi');parser.add_argument('--repository-id')
    parser.add_argument('--smtp-host');parser.add_argument('--smtp-port',type=int,default=25);parser.add_argument('--smtp-mode',choices=['starttls','tls'],default='starttls')
    parser.add_argument('--ca-file');parser.add_argument('--timeout',type=int,default=10);parser.add_argument('--output',required=True)
    args=parser.parse_args()
    try:
        if args.doi and not args.dataset_id:raise ValueError('--doi requiere --dataset-id.')
        v=Validator(args.base_url,args.timeout,args.ca_file);v.web()
        if args.dataset_id:v.dataset(args.dataset_id,args.doi)
        if args.repository_id:v.tracker_library();v.tracker(args.repository_id)
        if args.smtp_host:v.smtp(args.smtp_host,args.smtp_port,args.smtp_mode)
        report=v.report()
        # New evidence files are private by default; no overwrite of existing reports.
        fd=os.open(args.output,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'w') as out:json.dump(report,out,ensure_ascii=False,indent=2);out.write('\n')
        print(json.dumps({'checks':len(v.checks),'failures':sum(c['status']=='FAIL' for c in v.checks),'inconclusive':sum(c['status']=='INCONCLUSIVE' for c in v.checks),'output':str(Path(args.output))},ensure_ascii=False))
        return 1 if any(c['status']=='FAIL' for c in v.checks) else 2 if any(c['status']=='INCONCLUSIVE' for c in v.checks) else 0
    except (ValueError,OSError) as error:parser.error(str(error))
if __name__=='__main__':raise SystemExit(main())
