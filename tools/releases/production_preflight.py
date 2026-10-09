#!/usr/bin/env python3
"""Read-only production configuration gate. Reports key names, never values/secrets.
Does not contact services, change permissions, alter databases or validate credentials.
"""
import argparse, json, os, re
from pathlib import Path
from urllib.parse import urlsplit

def inspect(filename, strict_schema=False):
    values={};errors=[];warnings=[]
    for number,line in enumerate(Path(filename).read_text().splitlines(),1):
        if not line.strip() or line.lstrip().startswith(('#','!')):continue
        match=re.match(r'^\s*([^:=]+?)\s*[:=]\s*(.*)$',line)
        if not match:errors.append(f'Unsupported property syntax at line {number}');continue
        key,value=match.groups()
        if key in values:errors.append(f'Duplicate property: {key}')
        placeholder=re.fullmatch(r'\$\{([A-Za-z_][A-Za-z0-9_]*)(?::(.*))?\}',value)
        if placeholder:value=os.environ.get(placeholder[1],placeholder[2] or '')
        values[key]=value
    def require(condition,message):
        if not condition:errors.append(message)
    def true(key):return values.get(key,'').lower()=='true'
    require(true('repo.auth.enabled'),'Enable repo.auth.enabled')
    require(values.get('management.endpoints.web.exposure.include','health')=='health','Expose only Actuator health in production')
    require(len(values.get('repo.auth.jwtSecret','').encode())>=32,'Set a unique repo.auth.jwtSecret of at least 32 bytes')
    require(values.get('repo.auth.bootstrap-admin-password') not in ('admin12345','admin','password'),'Remove the known bootstrap admin password and rotate existing credentials')
    require(values.get('spring.datasource.url','').startswith('jdbc:postgresql:'),'Production requires PostgreSQL datasource')
    require(bool(values.get('spring.datasource.password')),'Set spring.datasource.password externally')
    schema=values.get('spring.jpa.hibernate.ddl-auto','').lower()
    require(schema not in ('create','create-drop'),'Destructive Hibernate schema mode is forbidden')
    if schema!='validate':
        (errors if strict_schema else warnings).append('Apply reviewed migrations and set spring.jpa.hibernate.ddl-auto=validate')
    require(values.get('repo.basepath','').startswith('file:/') and values.get('repo.basepath')!='file:/','Set repo.basepath to an absolute persistent local directory')
    domain=values.get('repo.public-domain','')
    require(bool(domain) and domain not in ('localhost','127.0.0.1') and '/' not in domain,'Set the real repo.public-domain')
    origin=values.get('repo.security.allowedOriginPattern','')
    require(origin.startswith('https://') and '*' not in origin,'Set repo.security.allowedOriginPattern to the exact public HTTPS origin')
    require(values.get('server.forward-headers-strategy')=='framework','Configure forwarded headers for remote HAProxy')
    require(bool(values.get('repo.deploy.haproxy-network')),'Set the trusted HAProxy network and restrict app access at the firewall')
    require(values.get('repo.mail.delivery-mode','SMTP')=='SMTP','Disable local mail capture in production')
    require(bool(values.get('spring.mail.host')) and bool(values.get('repo.mail.from')),'Configure real SMTP host and sender')
    require(true('spring.mail.properties.mail.smtp.starttls.required') or true('spring.mail.properties.mail.smtp.ssl.enable'),'Require SMTP encryption')
    require(values.get('spring.mail.properties.mail.smtp.ssl.checkserveridentity','true').lower()=='true','Enable SMTP certificate hostname verification')
    require(values.get('spring.mail.properties.mail.smtp.ssl.trust')!='*','Do not trust all SMTP certificates')
    require(true('repo.privacy.require-assessment'),'Enable required privacy assessment')
    if not true('repo.search.enabled'):warnings.append('Elasticsearch disabled; explicitly approve degraded search before release')
    warnings.append('Not verified here: DNS/TLS/HAProxy rules, SMTP delivery, DOI credentials, backup restoration and migration compatibility')
    return {'ready':not errors,'errors':errors,'warnings':warnings}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--config',required=True);parser.add_argument('--strict-schema',action='store_true');args=parser.parse_args()
    try:result=inspect(args.config,args.strict_schema)
    except (OSError,UnicodeError):result={'ready':False,'errors':['Cannot read configuration'],'warnings':[]}
    print(json.dumps(result,ensure_ascii=False,indent=2));return 0 if result['ready'] else 1
if __name__=='__main__':raise SystemExit(main())
