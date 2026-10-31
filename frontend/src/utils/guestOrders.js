/** Guest requests are preparation data. Sharing is never evidence of delivery. */
export function addDish(cart,dish){
 if(dish.priceTag==null||!Number.isFinite(Number(dish.priceTag))||Number(dish.priceTag)<0)throw new Error('This dish cannot be priced.');
 const current=cart.find(x=>x.dishId===dish.id);if(current)return setQuantity(cart,dish.id,current.quantity+1);
 if(cart.length>=50)throw new Error('Choose at most 50 distinct dishes.');
 return [...cart,{dishId:dish.id,name:dish.dishTitle,expectedUnitPrice:String(dish.priceTag),quantity:1,note:''}];
}
export function setQuantity(cart,id,quantity){if(!Number.isInteger(quantity)||quantity<0||quantity>99)throw new Error('Quantity must be a whole number from 1 to 99.');return quantity===0?cart.filter(x=>x.dishId!==id):cart.map(x=>x.dishId===id?{...x,quantity}:x);}
export function tableMenuUrl(slug,token,origin=window.location.origin){if(!/^[A-Za-z0-9_-]{43}$/.test(token))throw new Error('Invalid table identity');const url=new URL(`/r/${encodeURIComponent(slug)}`,origin);url.searchParams.set('table',token);return url.href;}
const xml=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
function wrap(value,maxWidth=904,fontSize=26,measure=(text,size)=>Array.from(text).length*size){
 const out=[];for(const paragraph of String(value??'').split(/\r?\n/)){let line='';for(const char of Array.from(paragraph)){if(line&&measure(line+char,fontSize)>maxWidth){let split=line.lastIndexOf(' ');if(split>0){out.push(line.slice(0,split));line=line.slice(split+1);}else{out.push(line);line='';}}line+=char;}out.push(line);}return out;
}
/** Vector layout is deterministic, paginated and contains no remote media/fonts. */
export function orderPages(snapshot,measure){
 const rows=[];const add=(text,strong=false)=>wrap(text,904,26,measure).forEach(t=>rows.push({text:t,strong}));
 add(snapshot.tableLabel?`Table: ${snapshot.tableLabel}`:'General restaurant request',true);
 add(`Prepared: ${new Date(snapshot.preparedAt).toISOString().replace('T',' ').slice(0,19)} UTC`);
 for(const line of snapshot.lines){add(`${line.quantity} × ${line.name}`,true);add(`${snapshot.currency} ${line.unitPrice} each · Line total ${line.lineTotal}`);if(line.note)add(`Note: ${line.note}`);add('');}
 add(`Estimated total: ${snapshot.currency} ${snapshot.estimatedTotal}`,true);if(snapshot.notes)add(`Notes: ${snapshot.notes}`);add('Prices and availability require restaurant confirmation. Please attach every page.');
 const title=wrap(snapshot.restaurantName,904,34,measure),disclaimerY=160+(title.length-1)*40+65,refY=disclaimerY+38,dividerY=refY+30,startY=dividerY+42;
 const count=Math.max(1,Math.floor((1350-startY)/34)),chunks=[];for(let i=0;i<rows.length;i+=count)chunks.push(rows.slice(i,i+count));
 const accent=/^#[0-9a-f]{6}$/i.test(snapshot.themeAccent||'')?snapshot.themeAccent:'#d5b575';
 return chunks.map((chunk,index)=>`<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1500" viewBox="0 0 1080 1500"><rect width="1080" height="1500" fill="#101114"/><rect x="48" y="48" width="984" height="1404" rx="28" fill="#1a1b1f" stroke="${accent}"/><g font-family="Arial,sans-serif" fill="#f7f0e2"><text x="88" y="108" font-size="18" letter-spacing="4" fill="${accent}">ATELIER · RESTAURANT REQUEST</text>${title.map((t,i)=>`<text x="88" y="${160+i*40}" font-size="34" font-weight="bold">${xml(t)}</text>`).join('')}<text x="88" y="${disclaimerY}" font-size="28" fill="${accent}">Order Request — Not Yet Accepted</text><text x="88" y="${refY}" font-size="19">Reference: ${xml(snapshot.reference)}</text><path d="M88 ${dividerY}H992" stroke="${accent}"/>${chunk.map((row,i)=>`<text x="88" y="${startY+i*34}" font-size="26" font-weight="${row.strong?'bold':'normal'}">${xml(row.text)}</text>`).join('')}<text x="88" y="1410" font-size="20" fill="${accent}">Page ${index+1} / ${chunks.length} · Guest must send · Acceptance pending</text></g></svg>`);
}
export async function renderOrderImages(snapshot){
 await document.fonts?.ready;const canvas=document.createElement('canvas');canvas.width=1080;canvas.height=1500;const context=canvas.getContext('2d');if(!context)throw new Error('Image generation is unavailable on this device.');const files=[];
 for(const [index,svg] of orderPages(snapshot,(text,size)=>{context.font=`bold ${size}px Arial,sans-serif`;return context.measureText(text).width;}).entries()){
  const url=URL.createObjectURL(new Blob([svg],{type:'image/svg+xml'}));try{const image=new Image();await new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=()=>reject(new Error('Image rendering failed. Please try another device.'));image.src=url;});context.clearRect(0,0,1080,1500);context.drawImage(image,0,0);const blob=await new Promise((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(new Error('PNG export failed.')),'image/png'));files.push(new File([blob],`atelier-request-${snapshot.reference}-${index+1}.png`,{type:'image/png'}));}finally{URL.revokeObjectURL(url);}
 }
 canvas.width=0;canvas.height=0;return files;
}
export function whatsappUrl(snapshot){if(!/^[1-9][0-9]{9,14}$/.test(snapshot.recipient||''))throw new Error('A valid restaurant contact is unavailable.');const text=`Order Request — Not Yet Accepted\n${snapshot.restaurantName}\nReference: ${snapshot.reference}\n${snapshot.tableLabel?`Table: ${snapshot.tableLabel}`:'General request'}\nEstimated total: ${snapshot.currency} ${snapshot.estimatedTotal}\nPlease attach all downloaded image pages manually and press Send. Await restaurant confirmation.`;return `https://wa.me/${snapshot.recipient}?text=${encodeURIComponent(text)}`;}
export async function shareOrderFiles(files,snapshot,device=navigator){if(!files.length||!device.canShare?.({files})||!device.share)return 'unsupported';try{await device.share({files,title:`${snapshot.restaurantName} — Order Request`,text:'Order Request — Not Yet Accepted. Please confirm with the restaurant.'});return 'handoff';}catch(e){if(e.name==='AbortError')return 'cancelled';throw e;}}
