import { useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import GuestMenuPage from '../../pages/GuestMenuPage.jsx';

/** Same React guest renderer, with a real isolated CSS viewport and no injected scripts. */
export default function BrandPreview({slug,appearance,size}) {
  const frame=useRef(null);
  const [body,setBody]=useState(null);
  function ready(){
    const doc=frame.current?.contentDocument;if(!doc)return;
    doc.head.replaceChildren();
    const title=doc.createElement('title');title.textContent='Atelier guest appearance preview';doc.head.append(title);
    for(const style of document.head.querySelectorAll('style,link[rel="stylesheet"]'))doc.head.append(style.cloneNode(true));
    doc.body.className='bg-obsidian text-zinc-100 antialiased';doc.body.style.margin='0';setBody(doc.body);
  }
  return <iframe ref={frame} title={`Live guest menu ${size} preview`} src="about:blank" onLoad={ready} className="mx-auto block h-[720px] max-h-[75vh] w-full rounded-2xl border border-white/15" style={{maxWidth:size==='mobile'?390:'100%'}}>{body&&createPortal(<div onClickCapture={e=>{if(e.target.closest?.('a')){e.preventDefault();e.stopPropagation();}}}><GuestMenuPage slug={slug} previewOnly studioAppearance={appearance}/></div>,body)}</iframe>;
}
