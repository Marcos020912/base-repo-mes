/* Conservative structural v1 baseline, not a substitute for consumer tests. */
const assert=(value,message)=>{if(!value)throw Error(message);};
function clean(schema){
 if(!schema||typeof schema!=='object')return schema;const out={};
 for(const key of ['$ref','type','format','enum','required','nullable'])if(schema[key]!==undefined)out[key]=schema[key];
 if(schema.properties)out.properties=Object.fromEntries(Object.entries(schema.properties).map(([k,v])=>[k,clean(v)]));
 if(schema.items)out.items=clean(schema.items);
 if(schema.additionalProperties!==undefined)out.additionalProperties=clean(schema.additionalProperties);
 for(const key of ['allOf','oneOf','anyOf'])if(schema[key])out[key]=schema[key].map(clean);
 return out;
}
function content(value){return Object.fromEntries(Object.entries(value||{}).map(([k,v])=>[k,{schema:clean(v.schema)}]));}
function snapshot(document){
 const operations={};
 for(const [path,item]of Object.entries(document.paths||{}))for(const [method,op]of Object.entries(item)){
  if(!path.startsWith('/api/v1/')||!['get','post','put','patch','delete','head','options'].includes(method))continue;
  operations[method.toUpperCase()+' '+path]={operationId:op.operationId,security:op.security,parameters:(op.parameters||[]).map(p=>({name:p.name,in:p.in,required:!!p.required,schema:clean(p.schema)})),requestBody:op.requestBody?{required:!!op.requestBody.required,content:content(op.requestBody.content)}:undefined,responses:Object.fromEntries(Object.entries(op.responses||{}).map(([code,r])=>[code,{content:content(r.content),headers:r.headers}]))};
 }
 return {schema:'reduniv.api-structural-baseline.v1',operations,schemas:Object.fromEntries(Object.entries(document.components?.schemas||{}).map(([name,s])=>[name,clean(s)]))};
}
function shape(current,previous,path){
 if(!previous||typeof previous!=='object')return;
 assert(current&&typeof current==='object','Removed contract shape: '+path);
 for(const key of ['$ref','type','format'])if(previous[key]!==undefined)assert(current[key]===previous[key],'Changed '+key+': '+path);
 for(const value of previous.enum||[])assert(current.enum?.includes(value),'Removed enum value: '+path);
 for(const field of current.required||[])assert((previous.required||[]).includes(field),'New mandatory field: '+path+'.'+field);
 for(const [name,value]of Object.entries(previous.properties||{}))shape(current.properties?.[name],value,path+'.'+name);
 if(previous.items)shape(current.items,previous.items,path+'[]');
 for(const key of ['allOf','oneOf','anyOf'])if(previous[key]){assert(current[key]?.length===previous[key].length,'Changed schema composition: '+path);previous[key].forEach((item,i)=>shape(current[key][i],item,path+'.'+key+i));}
 if(previous.additionalProperties&&typeof previous.additionalProperties==='object')shape(current.additionalProperties,previous.additionalProperties,path+'.additionalProperties');
}
function check(document,baseline){
 assert(baseline.schema==='reduniv.api-structural-baseline.v1','Unknown baseline schema');const current=snapshot(document);
 for(const [name,old]of Object.entries(baseline.operations)){
  const op=current.operations[name];assert(op,'Removed operation: '+name);assert(op.operationId===old.operationId,'Changed operationId: '+name);
  assert(JSON.stringify(op.security)===JSON.stringify(old.security),'Changed security boundary: '+name);
  for(const parameter of old.parameters){const now=op.parameters.find(p=>p.name===parameter.name&&p.in===parameter.in);assert(now,'Removed parameter: '+name+' '+parameter.name);assert(now.required===parameter.required,'Changed parameter requirement: '+name);shape(now.schema,parameter.schema,name+' '+parameter.name);}
  for(const parameter of op.parameters)if(parameter.required)assert(old.parameters.some(p=>p.name===parameter.name&&p.in===parameter.in),'New required parameter: '+name);
  if(op.requestBody?.required)assert(old.requestBody?.required,'New mandatory request body: '+name);
  for(const [mime,body]of Object.entries(old.requestBody?.content||{}))shape(op.requestBody?.content?.[mime]?.schema,body.schema,name+' request '+mime);
  for(const [code,response]of Object.entries(old.responses||{})){assert(op.responses?.[code],'Removed response: '+name+' '+code);for(const [mime,body]of Object.entries(response.content||{}))shape(op.responses[code].content?.[mime]?.schema,body.schema,name+' response '+mime);}
 }
 for(const [name,schema]of Object.entries(baseline.schemas))shape(current.schemas[name],schema,'schema '+name);
 return {operations:Object.keys(baseline.operations).length,schemas:Object.keys(baseline.schemas).length};
}
module.exports={snapshot,check};
