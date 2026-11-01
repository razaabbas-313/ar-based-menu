import test from 'node:test';
import assert from 'node:assert/strict';
import { appearanceStyle, PRESETS } from '../src/utils/appearance.js';
test('curated appearance tokens reject CSS injection and never introduce remote fonts',()=>{
 const style=appearanceStyle({...PRESETS.LUXURY,accent:'red;url(javascript:bad)',heading:'https://bad.test/font'});assert.equal(style['--brand-accent'],PRESETS.LUXURY.accent);assert.ok(!JSON.stringify(style).includes('javascript:'));assert.ok(!JSON.stringify(style).includes('https://'));
});
test('theme tokens support curated layouts, contrast pairs and reduced motion',()=>{for(const p of Object.values(PRESETS)){const s=appearanceStyle(p);assert.match(s['--brand-bg'],/^#[a-f0-9]{6}$/i);assert.match(s['--brand-text'],/^#[a-f0-9]{6}$/i);}assert.equal(appearanceStyle({...PRESETS.LUXURY,motion:'reduced'})['--brand-motion'],'0ms');});

test('published images and layout resolve correctly for split frontend/API origins',async()=>{
 const {brandedRestaurant}=await import('../src/utils/appearance.js');
 const original={name:'Cafe',logoUrl:'https://example.test/legacy.webp',menuLayout:'cards'};
 const brand={logoUrl:'/api/v1/public/brand-assets/00000000-0000-4000-8000-000000000001',bannerUrl:'https://example.test/private-preview',layout:'compact'};
 const result=brandedRestaurant(original,brand,'https://api.example.test');assert.equal(result.logoUrl,'https://api.example.test'+brand.logoUrl);assert.equal(result.bannerUrl,brand.bannerUrl);assert.equal(result.menuLayout,'compact');assert.equal(brandedRestaurant(original,null,'').logoUrl,original.logoUrl);
});
