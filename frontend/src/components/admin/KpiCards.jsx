import { Box, HardDrive, Smartphone, Store, Activity, UtensilsCrossed } from 'lucide-react';
import { cx, fmtBytes } from '../../utils/format.js';

/** Glowing platform KPIs for Super Admin. */
export default function KpiCards({ analytics }) {
  const a = analytics;

  const cards = [
    {
      key: 'restaurants',
      label: 'Restaurants',
      value: a ? a.totalRestaurants.toLocaleString() : '–',
      sub: a ? `${a.activeRestaurants} active · ${a.pendingRestaurants || 0} onboarding` : '',
      icon: Store,
      tone: 'amber',
    },
    {
      key: 'active',
      label: 'Active Tenants',
      value: a ? a.activeRestaurants.toLocaleString() : '–',
      sub: a ? `${a.suspendedRestaurants || 0} suspended` : '',
      icon: Activity,
      tone: 'emerald',
    },
    {
      key: 'models',
      label: '3D Concierge',
      value: a ? (a.modelsPublished || a.total3dModels || 0).toLocaleString() : '–',
      sub: a ? `${a.modelsInProduction || 0} in production` : 'Digital twins',
      icon: Box,
      tone: 'amber',
    },
    {
      key: 'ar',
      label: 'Guest AR Launches',
      value: a ? (a.totalArLaunches || 0).toLocaleString() : '–',
      sub: a ? `${(a.totalMenuViews || 0).toLocaleString()} menu views` : 'Table projections',
      icon: Smartphone,
      tone: 'emerald',
    },
    {
      key: 'storage',
      label: 'AWS S3 Storage',
      value: a ? fmtBytes(a.totalStorageBytes) : '–',
      sub: a ? `${a.totalDishes} dishes in cloud` : 'Tenants media',
      icon: HardDrive,
      tone: 'amber',
    },
  ];

  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
      {cards.map(({ key, label, value, sub, icon: Icon, tone }, i) => (
        <div
          key={key}
          data-testid={`kpi-${key}`}
          className={cx(
            'kpi-glow relative overflow-hidden rounded-3xl border hairline bg-onyx p-4 sm:p-5',
            i === 4 && 'col-span-2 lg:col-span-1',
          )}
        >
          <div className="flex items-center justify-between">
            <span className="text-[10px] font-semibold uppercase tracking-[0.24em] text-zinc-500">{label}</span>
            <Icon
              size={17}
              className={tone === 'emerald' ? 'text-emerald-300' : 'text-amber-300'}
              aria-hidden="true"
            />
          </div>
          <div className="mt-3 font-display text-4xl font-semibold leading-none text-zinc-50">{value}</div>
          <div className="mt-2 truncate text-xs text-zinc-500">{sub}</div>
        </div>
      ))}
    </div>
  );
}
