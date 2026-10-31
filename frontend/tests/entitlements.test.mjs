import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
import { build } from 'esbuild';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const source=(await fs.readFile(path.join(root,'src/api/client.js'),'utf8')).replaceAll('import.meta.env','({})');
const {api,setAdminToken,setAuthToken}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
const calls=[];
globalThis.fetch=async(url,init)=>{calls.push({url,init});return new Response(JSON.stringify({saved:true}),{status:200,headers:{'Content-Type':'application/json'}});};
setAdminToken('synthetic-admin-token');setAuthToken('synthetic-owner-token');

test('plan and revision writes use real client contracts with administrator auth',async()=>{
 const value={name:'Synthetic plan',description:'',active:true,expectedVersion:2,values:{'quota.dishes':4}};
 await api.createPlan(value);await api.revisePlan(7,value);await api.planRevisions(7);
 const [create,revise,revisions]=calls.splice(0);assert.equal(create.url,'/api/v1/admin/plans');assert.equal(create.init.method,'POST');assert.deepEqual(JSON.parse(create.init.body),value);
 assert.equal(revise.url,'/api/v1/admin/plans/7');assert.equal(revise.init.method,'PUT');assert.equal(revisions.url,'/api/v1/admin/plans/7/revisions');
 for(const c of [create,revise,revisions]){assert.equal(c.init.headers.Authorization,'Bearer synthetic-admin-token');assert.equal(c.init.cache,'no-store');}
});
test('assignment and overrides preserve values, version, reason and expiration',async()=>{
 const write={value:false,expectedVersion:3,reason:'Synthetic exception',expiresAt:'2026-12-01T00:00:00Z'};
 await api.assignPlan(9,{revisionId:4,expectedVersion:2,reason:'Explicit assignment'});await api.setEntitlementOverride(9,'model.ownerUpload',write);await api.removeEntitlementOverride(9,'model.ownerUpload',4,'Restore default');
 const [assign,override,remove]=calls.splice(0);assert.equal(assign.url,'/api/v1/admin/restaurants/9/entitlements/assignment');assert.deepEqual(JSON.parse(override.init.body),write);assert.equal(override.init.method,'PUT');assert.equal(remove.init.method,'DELETE');assert.equal(remove.url,'/api/v1/admin/restaurants/9/entitlements/overrides/model.ownerUpload');assert.deepEqual(JSON.parse(remove.init.body),{expectedVersion:4,reason:'Restore default'});
});
test('owner policy uses the owner session and does not accept a target tenant',async()=>{
 await api.ownerEntitlements();let c=calls.pop();assert.equal(c.url,'/api/v1/studio/entitlements');assert.equal(c.init.headers.Authorization,'Bearer synthetic-owner-token');assert.equal(c.init.body,undefined);
});
test('failed saves reject with server field errors instead of reporting success',async()=>{
 const previous=globalThis.fetch;globalThis.fetch=async()=>new Response(JSON.stringify({detail:'Configuration changed',fields:{expectedVersion:'Reload'}}),{status:409,headers:{'Content-Type':'application/json'}});
 try{await assert.rejects(api.revisePlan(1,{}),e=>e.status===409&&e.fields.expectedVersion==='Reload'&&e.message==='Configuration changed');}finally{globalThis.fetch=previous;}
});

// Existing Vite/esbuild and React are reused; no DOM emulator or dependency installation.
const output=path.join(root,'node_modules/.cache/entitlements-component-check.cjs');
await build({stdin:{contents:`import React from 'react';import{renderToStaticMarkup as render}from'react-dom/server';import Fields,{EntitlementInput,displayValue}from'./src/components/admin/EntitlementFields.jsx';import Plans from'./src/components/admin/PlansManager.jsx';import Restaurant from'./src/components/admin/RestaurantEntitlements.jsx';import Owner from'./src/components/studio/OwnerPlanOverview.jsx';export{React,render,Fields,EntitlementInput,displayValue,Plans,Restaurant,Owner};`,resolveDir:root,loader:'jsx'},bundle:true,platform:'node',format:'cjs',jsx:'automatic',outfile:output,define:{'import.meta.env':'{}'},logLevel:'silent'});
const require=createRequire(import.meta.url);const ui=require(output);
test('feature controls render accessible switches and preserve explicit false and zero',()=>{
 const toggle=ui.render(ui.React.createElement(ui.EntitlementInput,{definition:{key:'x',type:'BOOLEAN',label:'Model uploads'},value:false,onChange(){}}));assert.match(toggle,/role="switch"/);assert.match(toggle,/aria-checked="false"/);
 const limit=ui.render(ui.React.createElement(ui.EntitlementInput,{definition:{key:'x',type:'LIMIT',maximum:100,label:'Dishes'},value:0,onChange(){}}));assert.match(limit,/value="0"/);assert.equal(ui.displayValue(false),'Disabled');assert.equal(ui.displayValue(0),'0');assert.equal(ui.displayValue('UNLIMITED'),'Unlimited');
});
test('planned capabilities are explicitly labelled rather than operational',()=>{
 const html=ui.render(ui.React.createElement(ui.Fields,{definitions:[{key:'ordering.enabled',type:'BOOLEAN',implemented:false,label:'WhatsApp ordering'}],values:{'ordering.enabled':true},onChange(){}}));assert.match(html,/Planned product/);assert.match(html,/permission only/);
});
test('all policy screens provide an initial loading state without invented live plan data',()=>{
 for(let Component of [ui.Plans,ui.Restaurant,ui.Owner]){let html=ui.render(ui.React.createElement(Component,{restaurantId:1}));assert.ok(html.length>0);assert.doesNotMatch(html,/Starter|Growth|Enterprise/);}
});

test('AR policy removes custom-element AR flags while preserving 3D camera controls',async()=>{
 const {setViewerFlags}=await import('../src/utils/modelViewer.js');const attributes=new Set();const element={toggleAttribute(name,on){on?attributes.add(name):attributes.delete(name);}};
 setViewerFlags(element,{arEnabled:true});assert.equal(attributes.has('ar'),true);
 setViewerFlags(element,{arEnabled:false,autoRotate:false,isCard:false});assert.equal(attributes.has('ar'),false);assert.equal(attributes.has('xr-environment'),false);assert.equal(attributes.has('camera-controls'),true);assert.equal(attributes.has('auto-rotate'),false);
});
