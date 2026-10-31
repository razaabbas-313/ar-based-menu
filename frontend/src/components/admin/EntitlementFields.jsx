import { useId } from 'react';
import { inputClass } from '../ui.jsx';

export function displayValue(value) {
  if (Array.isArray(value)) return value.map(v => ({ SIMPLE: 'Simple', THREE_D: '3D', AR: 'AR' }[v] || v)).join(' · ');
  if (typeof value === 'boolean') return value ? 'Enabled' : 'Disabled';
  if (value === 'UNLIMITED') return 'Unlimited';
  return typeof value === 'number' ? value.toLocaleString() : '—';
}

export function EntitlementInput({ definition, value, onChange, disabled = false, id }) {
  if (definition.type === 'BOOLEAN') return <button id={id} type="button" role="switch" aria-checked={!!value} aria-label={definition.label} disabled={disabled} onClick={() => onChange(!value)} className={`relative h-7 w-12 shrink-0 rounded-full border transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-amber-300 disabled:opacity-50 ${value ? 'border-amber-400 bg-amber-400' : 'border-zinc-600 bg-zinc-800'}`}><span className={`absolute top-0.5 h-5 w-5 rounded-full ${value ? 'right-0.5 bg-zinc-950' : 'left-0.5 bg-zinc-400'}`} /></button>;
  if (definition.type === 'MODES') return <div id={id} role="group" aria-label={definition.label} className="flex flex-wrap gap-3">{['SIMPLE', 'THREE_D', 'AR'].map(mode => <label key={mode} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={(value || []).includes(mode)} disabled={disabled || mode === 'SIMPLE'} onChange={e => onChange(e.target.checked ? [...value, mode] : value.filter(v => v !== mode))} className="accent-amber-400" />{{ SIMPLE: 'Simple', THREE_D: '3D', AR: 'AR' }[mode]}</label>)}</div>;
  return <div className="flex flex-wrap items-center gap-3">
    <input id={id} aria-label={definition.label} type="number" min="0" max={definition.maximum} step="1" disabled={disabled || value === 'UNLIMITED'} value={value === 'UNLIMITED' ? '' : value ?? 0} onChange={e => onChange(e.target.value === '' ? 0 : Number(e.target.value))} className={`${inputClass} !w-40`} />
    {definition.type === 'LIMIT' && <label className="flex items-center gap-2 text-xs text-zinc-400"><input type="checkbox" aria-label={`Unlimited ${definition.label}`} checked={value === 'UNLIMITED'} disabled={disabled} onChange={e => onChange(e.target.checked ? 'UNLIMITED' : 0)} className="accent-amber-400" />Unlimited</label>}
    {definition.type === 'BYTES' && <span className="text-xs text-zinc-500">bytes · global upload limits still apply</span>}
  </div>;
}

export default function EntitlementFields({ definitions, values, onChange, disabled }) {
  const fieldId = useId();
  const groups = [
    ['Menu & guest experience', d => d.key.startsWith('menu.') || d.key.startsWith('qr.') || ['ordering.enabled', 'waiter.enabled', 'reviews.enabled'].includes(d.key)],
    ['3D & restaurant tools', d => d.key.startsWith('model.') || d.key.startsWith('branding.') || d.key.startsWith('analytics.')],
    ['Capacity & limits', d => d.key.startsWith('quota.')],
  ];
  return <div className="space-y-7">{groups.map(([title, matches]) => <fieldset key={title} className="min-w-0"><legend className="mb-2 text-sm font-semibold text-zinc-200">{title}</legend><div className="divide-y divide-white/5">{definitions.filter(matches).map(d => <div key={d.key} className="flex flex-wrap items-center justify-between gap-3 py-3"><div className="min-w-0"><label htmlFor={`${fieldId}-${d.key}`} className="text-sm text-zinc-300">{d.label}</label>{!d.implemented && <div className="mt-1 text-xs text-zinc-500">Planned product · permission only</div>}</div><EntitlementInput id={`${fieldId}-${d.key}`} definition={d} value={values[d.key]} disabled={disabled} onChange={value => onChange(d.key, value)} /></div>)}</div></fieldset>)}</div>;
}
