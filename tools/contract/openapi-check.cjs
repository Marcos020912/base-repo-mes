#!/usr/bin/env node
/* Structural compatibility gate for the scientific HTTP v1 surface. Never sends writes. */
function assert(value,message){if(!value)throw Error(message);}
const expected={
 '/api/v1/public/resources/{id}/usage-configuration':['get'],
 '/api/v1/scientific/operations/self-assessment':['get'], '/api/v1/scientific/operations/self-assessment/{id}':['put'],
 '/api/v1/public/usage':['get'], '/api/v1/scientific/operations/usage':['get'],
 '/api/v1/catalog':['get'], '/api/v1/scientific/{id}':['get','put'], '/api/v1/scientific/{id}/submit':['post'],
 '/api/v1/scientific/{id}/publish':['post'], '/api/v1/scientific/{id}/privacy':['get','put'],
 '/api/v1/scientific/{id}/privacy/review':['post'], '/api/v1/scientific/privacy-policy':['get'],
 '/api/v1/scientific/preservation/storage':['get'], '/api/v1/scientific/preservation/audits':['get','post'],
 '/api/v1/scientific/preservation/metrics':['get'], '/api/v1/my-deposit-tasks':['get'],
 '/api/v1/public/resources/{id}':['get'], '/api/v1/public/resources/{id}/versions':['get'],
 '/api/v1/public/resources/{id}/archive':['get'], '/api/v1/scientific/{id}/citation':['get'],
 '/api/v1/public/metrics':['get'], '/api/v1/public/collections':['get'], '/api/v1/collections':['get','post'],
 '/api/v1/scientific/{id}/doi/reserve':['post'], '/api/v1/scientific/{id}/doi/publish':['post'],
 '/api/v1/scientific/{id}/doi/landing-targets':['get'], '/api/v1/scientific/{id}/doi/refresh-urls':['post']
};
function resolve(document,ref){return ref.slice(2).split('/').map(k=>k.replace(/~1/g,'/').replace(/~0/g,'~')).reduce((value,key)=>value?.[key],document);}
function dereference(document,schema){return schema?.$ref?resolve(document,schema.$ref):schema;}
function validate(document){
 assert(/^3\./.test(document.openapi),'No es un contrato OpenAPI3.');
 for(const [path,methods]of Object.entries(expected))for(const method of methods){const operation=document.paths?.[path]?.[method];assert(operation,`Falta operación ${method.toUpperCase()} ${path}`);if(path.startsWith('/api/v1/public/')||path==='/api/v1/scientific/{id}/citation')assert(Array.isArray(operation.security)&&operation.security.length===0,'Operación pública requiere JWT: '+path);else assert(operation.security?.some(rule=>'bearer-jwt'in rule),'Operación privada sin JWT documentado: '+path);}
 const ids=new Set();for(const path of Object.values(document.paths))for(const [method,operation]of Object.entries(path)){if(!['get','post','put','patch','delete','options','head','trace'].includes(method))continue;assert(operation.operationId,'Operación sin identificador estable.');assert(!ids.has(operation.operationId),'operationId duplicado: '+operation.operationId);ids.add(operation.operationId);}
 let operations=0;for(const [path,item]of Object.entries(document.paths))for(const [method,op]of Object.entries(item)) {
  if(!['get','post','put','patch','delete','options','head'].includes(method)||!path.startsWith('/api/v1/'))continue;
  operations++;assert(Array.isArray(op.security),'Missing explicit transport security: '+method+' '+path);
  assert(op.responses&&Object.keys(op.responses).length,'Missing responses: '+path);
  for(const parameter of op.parameters||[])if(parameter.in==='path')assert(parameter.required===true,'Optional path parameter: '+path);
  for(const requirement of op.security)for(const name of Object.keys(requirement))assert(document.components?.securitySchemes?.[name],'Unknown security scheme: '+name);
 }
 let references=0;function walk(value){if(!value||typeof value!=='object')return;if(value.$ref?.startsWith('#/')){assert(resolve(document,value.$ref),'Referencia local inexistente: '+value.$ref);references++;}Object.values(value).forEach(walk);}walk(document);
 const schema=document.components?.schemas?.ScientificRecordUpdate;assert(schema,'Falta ScientificRecordUpdate.');for(const key of ['summary','temporalStart','temporalEnd','geographicCoverage','translations','productionDescription','processingDescription','processingTools'])assert(schema.properties?.[key],'Campo científico fuera del contrato: '+key);
 const privateSchema=document.components.schemas.PrivatePrivacyAssessment;assert(privateSchema?.properties?.assessmentNote,'Falta evaluación privada.');
 const publicSchema=document.components.schemas.PublicScientificResource;assert(publicSchema?.properties?.productionDescription,'Falta ficha pública estructurada.');assert(!('assessmentNote'in publicSchema.properties)&&!('reviewNote'in publicSchema.properties),'Notas privadas declaradas en ficha pública.');
 const get=document.paths['/api/v1/public/resources/{id}'].get;assert(get.responses['410'],'Falta tombstone410.');
 const archive=document.paths['/api/v1/public/resources/{id}/archive'].get.responses['200'];assert(archive.content?.['application/zip']?.schema?.format==='binary','ZIP no documentado como binario.');
 const versions=document.components.schemas.PublicVersionHistoryPage;assert(versions?.properties?.latestPublishedId&&versions.properties.newerPublicationAvailable,'Familia pública incompleta.');
 const callback=document.paths['/api/v1/scientific/orcid/callback']?.get;assert(callback?.security?.length===0&&callback.responses['303'],'Callback ORCID documentado incorrectamente.');
 const reviewer=document.paths['/api/v1/reviewer/file']?.get;assert(reviewer?.security?.some(rule=>'reviewer-token'in rule),'Enlace revisor confundido con JWT.');
 const declaration=document.components.schemas.PrivacyDeclaration;assert(declaration?.required?.includes('classification'),'Clasificación obligatoria no documentada.');
 return {paths:Object.keys(expected).length,operations,localReferences:references,openapi:document.openapi};
}
module.exports={validate};
if(require.main===module)(async()=>{try{const base=new URL(process.argv[2]);assert(['127.0.0.1','localhost','[::1]'].includes(base.hostname)&&base.protocol==='http:','Este comprobador CLI solo consulta un backend local HTTP explícito.');const response=await fetch(new URL('/v3/api-docs',base));assert(response.ok,'No se pudo obtener el contrato local.');console.log(validate(await response.json()));}catch(error){console.error(error.message);process.exitCode=1;}})();
