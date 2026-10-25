import { useEffect, useState } from 'react';
import { buildQrMatrix, qrCardSvg } from '../../utils/qrCard.js';

/** Luxury acrylic table stand holding a real, scannable QR card (rendered with the QR suite). */
export default function TableStandMockup() {
  const [svg, setSvg] = useState('');

  useEffect(() => {
    let alive = true;
    const url = window.location.origin;
    buildQrMatrix(url)
      .then((matrix) => alive && setSvg(qrCardSvg({ matrix, name: 'Maison Lumière', url, theme: 'onyx', format: '4x6' })))
      .catch(() => alive && setSvg(''));
    return () => {
      alive = false;
    };
  }, []);

  return (
    <div className="relative mx-auto w-full max-w-[380px] [perspective:1400px]" data-testid="table-stand">
      {/* table surface */}
      <div className="absolute inset-x-[-30%] bottom-[-6%] h-[38%] rounded-[50%] bg-[radial-gradient(closest-side,rgba(120,80,40,0.45),rgba(20,14,10,0.0))]" />
      <div className="relative mx-auto w-[78%] [transform:rotateX(8deg)_rotateY(-14deg)] [transform-style:preserve-3d]">
        {/* acrylic block */}
        <div className="relative rounded-[22px] border border-white/25 bg-gradient-to-br from-white/[0.16] via-white/[0.05] to-white/[0.02] p-[7%] shadow-[0_40px_80px_-30px_rgba(0,0,0,0.95),inset_0_1px_0_rgba(255,255,255,0.35)] backdrop-blur-sm">
          <div className="overflow-hidden rounded-[10px] shadow-[0_8px_30px_rgba(0,0,0,0.6)]" style={{ aspectRatio: '1000 / 1500' }}>
            {svg ? (
              // Generated locally from constant, escaped values.
              <div className="h-full w-full" dangerouslySetInnerHTML={{ __html: svg }} />
            ) : (
              <div className="skeleton h-full w-full" />
            )}
          </div>
          {/* glass reflections */}
          <div className="pointer-events-none absolute inset-0 rounded-[22px] bg-[linear-gradient(115deg,rgba(255,255,255,0.22)_0%,rgba(255,255,255,0)_28%,rgba(255,255,255,0)_70%,rgba(255,255,255,0.1)_100%)]" />
          <div className="pointer-events-none absolute -right-px top-[6%] h-[88%] w-[6px] rounded-full bg-gradient-to-b from-white/40 via-white/10 to-white/30" />
        </div>
        {/* foot */}
        <div className="mx-auto -mt-1 h-5 w-[86%] rounded-b-[14px] border border-t-0 border-white/20 bg-gradient-to-b from-white/[0.14] to-white/[0.03]" />
        <div className="mx-auto h-3 w-[70%] rounded-[50%] bg-black/70 blur-md" />
      </div>
    </div>
  );
}
