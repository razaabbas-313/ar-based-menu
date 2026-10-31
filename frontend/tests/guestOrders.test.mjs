import test from 'node:test';
import assert from 'node:assert/strict';
import { addDish, setQuantity, orderPages, whatsappUrl, tableMenuUrl, shareOrderFiles } from '../src/utils/guestOrders.js';
test('cart preserves dishes without models and bounds quantities and distinct lines',()=>{
 let cart=addDish([],{id:1,dishTitle:'Soup',priceTag:'4.25'});cart=addDish(cart,{id:1,dishTitle:'Soup',priceTag:'4.25'});assert.equal(cart[0].quantity,2);assert.equal(setQuantity(cart,1,0).length,0);assert.throws(()=>setQuantity(cart,1,100));assert.throws(()=>setQuantity(cart,1,1.5));assert.throws(()=>addDish(Array.from({length:50},(_,id)=>({dishId:id+2})),{id:1,priceTag:3}));
});
const snapshot={restaurantName:'Synthetic & <Restaurant>',reference:'test-reference',preparedAt:'2026-10-31T12:00:00Z',tableLabel:'Terrace 1',currency:'USD',estimatedTotal:'12.00',notes:'N'.repeat(500),lines:Array.from({length:50},(_,i)=>({name:`Dish ${i} `+'long '.repeat(30),quantity:2,unitPrice:'3.00',lineTotal:'6.00',note:`Note ${i}`}))};
test('image layout paginates every line and notes, escapes markup and marks every page unaccepted',()=>{
 const pages=orderPages(snapshot);assert.ok(pages.length>1);const all=pages.join('');for(let i=0;i<50;i++){assert.ok(all.includes(`Dish ${i} `));assert.ok(all.includes(`Note ${i}`));}assert.ok(all.includes('&lt;Restaurant&gt;'));assert.ok(!all.includes('<Restaurant>'));for(const svg of pages){assert.match(svg,/Order Request/);assert.match(svg,/Not Yet Accepted/);assert.match(svg,/test-reference/);assert.match(svg,/Page/);}assert.match(all,/Estimated total/);
});
test('WhatsApp fallback only uses validated digits and explicitly asks manual attachment',()=>{
 const url=whatsappUrl({...snapshot,recipient:'15551234567'});assert.ok(url.startsWith('https://wa.me/15551234567?text='));assert.match(decodeURIComponent(url),/attach.*manually/i);assert.throws(()=>whatsappUrl({...snapshot,recipient:'1/?bad'}));assert.ok(url.length<4000);
});
test('opaque table URLs preserve legacy restaurant path and reject arbitrary labels',()=>{
 assert.equal(tableMenuUrl('cafe','a'.repeat(43),'https://example.test'),'https://example.test/r/cafe?table='+'a'.repeat(43));assert.throws(()=>tableMenuUrl('cafe','Table 1','https://example.test'));
});
test('share handoff never reports delivery and preserves cancellation or unsupported outcomes',async()=>{
 assert.equal(await shareOrderFiles([],snapshot,{}),'unsupported');assert.equal(await shareOrderFiles([{}],snapshot,{canShare:()=>true,share:async()=>{}}),'handoff');assert.equal(await shareOrderFiles([{}],snapshot,{canShare:()=>true,share:async()=>{throw Object.assign(new Error(),{name:'AbortError'});}}),'cancelled');await assert.rejects(shareOrderFiles([{}],snapshot,{canShare:()=>true,share:async()=>{throw new Error('Device failed');}}));
});

test('wide glyphs and long restaurant names stay within measured image text boundaries',()=>{
 const measure=(text,size)=>Array.from(text).reduce((n,c)=>n+(c==='W'?size:.75*size),0);
 const s={...snapshot,restaurantName:'W'.repeat(120),notes:'W'.repeat(500),lines:[{name:'W'.repeat(200),quantity:1,unitPrice:'1.00',lineTotal:'1.00',note:'W'.repeat(200)}]};
 const pages=orderPages(s,measure);for(const svg of pages){for(const match of svg.matchAll(/<text[^>]*font-size="(26|34)"[^>]*>([^<]*)<\/text>/g)){assert.ok(measure(match[2],Number(match[1]))<=904,`text overflow: ${match[2]}`);}}const words=pages.join('').match(/W+/g).join('');assert.equal(words.length,900+120*pages.length);
});

test('PNG export processes every page sequentially and revokes temporary vector URLs',async()=>{
 const {renderOrderImages}=await import('../src/utils/guestOrders.js');let draws=0,revoked=0;
 const previousDocument=globalThis.document,previousImage=globalThis.Image,oldRevoke=URL.revokeObjectURL;
 const context={font:'',measureText(text){return{width:Array.from(text).length*Number(this.font.match(/\d+/)[0])};},clearRect(){},drawImage(){draws++;}};
 globalThis.document={fonts:{ready:Promise.resolve()},createElement(){return {width:0,height:0,getContext:()=>context,toBlob(callback){callback(new Blob(['synthetic-png-contract-only'],{type:'image/png'}));}};}};
 globalThis.Image=class{set src(value){queueMicrotask(()=>this.onload());}};URL.revokeObjectURL=url=>{revoked++;oldRevoke.call(URL,url);};
 try{const images=await renderOrderImages(snapshot);assert.ok(images.length>1);assert.equal(draws,images.length);assert.equal(revoked,images.length);for(const [i,file] of images.entries()){assert.equal(file.type,'image/png');assert.ok(file.name.endsWith(`-${i+1}.png`));}}finally{globalThis.document=previousDocument;globalThis.Image=previousImage;URL.revokeObjectURL=oldRevoke;}
});
