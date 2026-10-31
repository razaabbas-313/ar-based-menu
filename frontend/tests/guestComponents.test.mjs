import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { build } from 'esbuild';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const output=path.join(root,'node_modules/.cache/guest-component-check.cjs');
await build({stdin:{contents:`import React from 'react';import{renderToStaticMarkup as render}from'react-dom/server';import Actions from './src/components/guest/GuestActions.jsx';import Sheet from './src/components/guest/DishSheet.jsx';import Setup from './src/components/studio/ExperienceManager.jsx';export{React,render,Actions,Sheet,Setup};`,resolveDir:root,loader:'jsx'},bundle:true,platform:'node',format:'cjs',jsx:'automatic',outfile:output,define:{'import.meta.env':'{}'},plugins:[{name:'ssr-portal',setup(b){b.onResolve({filter:/^react-dom$/},()=>({path:'portal',namespace:'test'}));b.onLoad({filter:/.*/,namespace:'test'},()=>({contents:'export const createPortal=(children)=>children;',loader:'js'}));}}],logLevel:'silent'});
const ui=createRequire(import.meta.url)(output);globalThis.document={body:{}};
const render=(C,props)=>ui.render(ui.React.createElement(C,props));
const dish={id:1,dishTitle:'Soup',priceTag:4.25,is3d:true,modelUrl:'https://example.test/synthetic.glb',available:true};
test('3D menu has model action and no AR launch; priced photo dishes can enter cart',()=>{
 const three=render(ui.Sheet,{item:dish,arAllowed:false,onClose(){},onAddToCart(){}});assert.match(three,/Launch 3D Model/);assert.doesNotMatch(three,/data-testid="launch-ar"/);assert.match(three,/Add to request/);
 const simple=render(ui.Sheet,{item:{...dish,is3d:false,modelUrl:null},arAllowed:false,onClose(){},onAddToCart(){}});assert.doesNotMatch(simple,/launch-3d|launch-ar|model-viewer/);assert.match(simple,/Add to request/);
 const ar=render(ui.Sheet,{item:dish,arAllowed:true,onClose(){}});assert.match(ar,/data-testid="launch-ar"/);
});
test('guest context fails closed for invalid tables and handles loading without outbound actions',()=>{
 const props={slug:'cafe',cart:[],setCart(){},notes:'',setNotes(){}};assert.match(render(ui.Actions,props),/Checking table/);
 const invalid=render(ui.Actions,{...props,context:{contextError:'This table QR is invalid',orderingAvailable:false,waiterAvailable:false}});assert.match(invalid,/Open restaurant menu/);assert.doesNotMatch(invalid,/Call Waiter|Your request|wa.me/);
 const active=render(ui.Actions,{...props,context:{tableLabel:'Terrace 01',orderingAvailable:true,waiterAvailable:true}});assert.match(active,/Terrace 01/);assert.match(active,/Call Waiter/);assert.match(active,/Your request/);assert.doesNotMatch(active,/href="https:\/\/wa.me/);
});
test('owner guest screen exposes its real loading state',()=>{assert.match(render(ui.Setup,{restaurant:{slug:'cafe',name:'Cafe'}}),/Loading guest experience/);});
const clientSource=(await fs.readFile(path.join(root,'src/api/client.js'),'utf8')).replaceAll('import.meta.env','({})');const {api,setAuthToken}=await import('data:text/javascript;base64,'+Buffer.from(clientSource).toString('base64'));
test('owner endpoints use owner auth and guest tokens travel in JSON with no-store',async()=>{
 const calls=[];globalThis.fetch=async(url,init)=>{calls.push({url,init});return new Response('{}',{headers:{'Content-Type':'application/json'}});};setAuthToken('synthetic-owner');
 await api.tables(200);await api.createTable('Terrace');await api.updateTable(1,{name:'One',active:false,expectedVersion:2});await api.rotateTable(1,3);await api.saveExperienceSettings({menuMode:'SIMPLE'});
 for(const call of calls){assert.equal(call.init.headers.Authorization,'Bearer synthetic-owner');assert.equal(call.init.cache,'no-store');}assert.equal(calls[0].url,'/api/v1/studio/tables?after=200');calls.length=0;
 await api.guestContext('cafe','opaque');await api.prepareOrder('cafe',{items:[],tableToken:'opaque'});await api.waiterRequest('cafe','opaque');for(const call of calls){assert.equal(call.init.method,'POST');assert.equal(call.init.headers.Authorization,undefined);assert.equal(call.init.cache,'no-store');assert.ok(!call.url.includes('opaque'));assert.equal(JSON.parse(call.init.body).tableToken,'opaque');}
});
