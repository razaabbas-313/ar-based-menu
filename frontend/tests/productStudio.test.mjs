import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
import { build } from 'esbuild';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const source=(await fs.readFile(path.join(root,'src/api/client.js'),'utf8')).replaceAll('import.meta.env','({})');
const {api,setAuthToken,setAdminToken}=await import('data:text/javascript;base64,'+Buffer.from(source).toString('base64'));
test('draft, publish, review and model APIs keep authenticated scopes and versions',async()=>{
 const calls=[];globalThis.fetch=async(url,init)=>{calls.push({url,init});return new Response('{}',{headers:{'Content-Type':'application/json'}});};setAuthToken('synthetic-owner');setAdminToken('synthetic-admin');
 await api.saveAppearance(3,{preset:'MINIMAL'});await api.publishAppearance(4);await api.discardAppearance(5);await api.saveReviewSettings({expectedVersion:2,enabled:true,url:'https://g.page/r/synthetic/review'});await api.draftImagePresign('LOGO',{filename:'synthetic.webp',contentType:'image/webp',uploadSizeBytes:5});await api.draftImageConfirm('synthetic-uuid');
 for(const call of calls){assert.equal(call.init.headers.Authorization,'Bearer synthetic-owner');assert.equal(call.init.cache,'no-store');}assert.deepEqual(JSON.parse(calls[0].init.body),{expectedVersion:3,values:{preset:'MINIMAL'}});assert.equal(JSON.parse(calls[1].init.body).expectedVersion,4);calls.length=0;
 await api.adminDirectModelPresign(9,3,{filename:'synthetic.glb'});await api.adminDirectModelConfirm(9,3,{s3Key:'synthetic-key'});await api.adminModelHistory(9,3,100);
 for(const call of calls){assert.equal(call.init.headers.Authorization,'Bearer synthetic-admin');assert.ok(call.url.startsWith('/api/v1/admin/restaurants/9/dishes/3/'));}assert.ok(calls[2].url.endsWith('model-history?after=100'));
});
test('new analytics separates anonymous observations, owner and platform reports',async()=>{
 const calls=[];globalThis.fetch=async(url,init)=>{calls.push({url,init});return new Response('{}',{headers:{'Content-Type':'application/json'}});};const input={slug:'synthetic',eventId:'00000000-0000-4000-8000-000000000001',eventType:'SHARE_REPORTED_COMPLETED',tableToken:'opaque'};
 await api.recordInsight(input);await api.insights('2026-10-01','2026-10-31');await api.platformInsights('2026-10-01','2026-10-31');
 assert.equal(calls[0].init.headers.Authorization,undefined);assert.deepEqual(JSON.parse(calls[0].init.body),input);assert.ok(!calls[0].url.includes('opaque'));assert.equal(calls[1].init.headers.Authorization,'Bearer synthetic-owner');assert.equal(calls[2].init.headers.Authorization,'Bearer synthetic-admin');assert.ok(calls[1].url.includes('from=2026-10-01'));
});
const output=path.join(root,'node_modules/.cache/product-studio-component-check.cjs');
await build({stdin:{contents:`import React from 'react';import{renderToStaticMarkup as render}from'react-dom/server';import Preview from './src/components/studio/BrandPreview.jsx';import Brand from './src/components/studio/BrandStudio.jsx';import Models from './src/components/studio/ModelsManager.jsx';import Reviews from './src/components/studio/ReviewSettings.jsx';import Insights from './src/components/studio/InsightDashboard.jsx';export{React,render,Preview,Brand,Models,Reviews,Insights};`,resolveDir:root,loader:'jsx'},bundle:true,platform:'node',format:'cjs',jsx:'automatic',outfile:output,define:{'import.meta.env':'{}'},logLevel:'silent'});
const ui=createRequire(import.meta.url)(output);const render=(C,p)=>ui.render(ui.React.createElement(C,p));
test('product screens show truthful loading and empty states without invented data',()=>{
 assert.match(render(ui.Brand,{restaurant:{slug:'synthetic'}}),/Preparing your design studio/);assert.match(render(ui.Reviews),/Loading review settings/);assert.match(render(ui.Insights),/Loading actual engagement data/);assert.match(render(ui.Insights),/do not establish delivery/);assert.match(render(ui.Models,{dishes:[]}),/No dishes match/);
});
test('model management keeps photo-only dishes visible and explains active quota capacity',()=>{
 const html=render(ui.Models,{dishes:[{id:1,dishTitle:'Photo-only soup',is3d:false}],policy:{operational:{'model.ownerUpload':true,'model.concierge':false},usage:{models:2},values:{'quota.models':3}}});assert.match(html,/Photo-only soup/);assert.match(html,/Upload model/);assert.match(html,/1 remaining/);assert.match(html,/History &amp; provenance/);
});

test('preview uses a script-free blank viewport without changing security headers',()=>{const html=render(ui.Preview,{slug:'synthetic',appearance:{},size:'mobile'});assert.match(html,/src="about:blank"/);assert.match(html,/max-width:390px/);assert.match(html,/title="Live guest menu mobile preview"/);assert.doesNotMatch(html,/<script|srcdoc=/i);});
