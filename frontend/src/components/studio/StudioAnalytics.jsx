import { useEffect, useState } from 'react';
import { Eye, Box, Smartphone, TrendingUp, RefreshCw, TriangleAlert, UtensilsCrossed } from 'lucide-react';
import { api } from '../../api/client.js';
import { Spinner } from '../ui.jsx';
import { cx } from '../../utils/format.js';

export default function StudioAnalytics() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = () => {
    setLoading(true);
    setError('');
    api
      .studioAnalytics()
      .then((res) => {
        setData(res);
        setLoading(false);
      })
      .catch((err) => {
        setError(err.message || 'Failed to load dining analytics');
        setLoading(false);
      });
  };

  useEffect(() => {
    load();
  }, []);

  if (loading && !data) {
    return (
      <div className="grid place-items-center py-24">
        <Spinner />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center gap-3 rounded-3xl border border-red-500/25 bg-red-500/5 px-6 py-14 text-center">
        <TriangleAlert size={28} className="text-red-400" />
        <p className="max-w-md text-sm text-red-200">{error}</p>
        <button
          type="button"
          onClick={load}
          className="tap mt-2 h-10 rounded-full border hairline px-5 text-sm font-semibold text-zinc-200"
        >
          Try again
        </button>
      </div>
    );
  }

  const kpis = [
    {
      key: 'menuViews',
      label: 'Guest Menu Scans',
      value: (data?.totalMenuViews || 0).toLocaleString(),
      sub: 'Total menu visits by guests',
      icon: Eye,
      tone: 'amber',
    },
    {
      key: 'engagementRate',
      label: '3D Interaction Rate',
      value: `${(data?.engagement3dRatePercent || 0).toFixed(1)}%`,
      sub: 'Guests who engaged in 3D',
      icon: TrendingUp,
      tone: 'emerald',
    },
    {
      key: 'modelViews',
      label: '3D Dish Explorations',
      value: (data?.totalModelViews || 0).toLocaleString(),
      sub: 'Total 3D models viewed',
      icon: Box,
      tone: 'amber',
    },
    {
      key: 'arLaunches',
      label: 'Table AR Projections',
      value: (data?.totalArLaunches || 0).toLocaleString(),
      sub: 'Live AR dishes placed on tables',
      icon: Smartphone,
      tone: 'emerald',
    },
  ];

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <div className="text-[10px] font-semibold uppercase tracking-[0.25em] text-amber-500/80">
            Real-Time Dining Engagement
          </div>
          <h2 className="font-display text-3xl font-semibold text-zinc-100">Guest Experience Analytics</h2>
        </div>
        <button
          type="button"
          onClick={load}
          className="tap flex h-10 items-center gap-2 rounded-full border hairline px-4 text-xs font-semibold text-zinc-300 hover:text-zinc-100"
        >
          <RefreshCw size={14} className={cx(loading && 'animate-spin')} />
          <span>Refresh</span>
        </button>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {kpis.map((kpi) => {
          const Icon = kpi.icon;
          return (
            <div
              key={kpi.key}
              className="kpi-glow relative overflow-hidden rounded-3xl border hairline bg-onyx p-5"
            >
              <div className="flex items-center justify-between">
                <span className="text-[10px] font-semibold uppercase tracking-[0.22em] text-zinc-500">
                  {kpi.label}
                </span>
                <div
                  className={cx(
                    'grid h-8 w-8 place-items-center rounded-xl',
                    kpi.tone === 'emerald'
                      ? 'bg-emerald-500/10 text-emerald-300'
                      : 'bg-amber-500/10 text-amber-300',
                  )}
                >
                  <Icon size={16} />
                </div>
              </div>
              <div className="mt-3 font-display text-3xl font-bold leading-none text-zinc-50">
                {kpi.value}
              </div>
              <div className="mt-2 text-xs text-zinc-500">{kpi.sub}</div>
            </div>
          );
        })}
      </div>

      {/* Top Dishes */}
      <div className="rounded-3xl border hairline bg-onyx p-6">
        <div className="mb-4 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <UtensilsCrossed size={18} className="text-amber-400" />
            <h3 className="font-display text-xl font-semibold text-zinc-100">Top 5 Most-Viewed Dishes</h3>
          </div>
          <span className="text-xs text-zinc-500">Ranked by guest attention</span>
        </div>

        {!data?.topDishes || data.topDishes.length === 0 ? (
          <div className="rounded-2xl border border-dashed hairline py-12 text-center text-sm text-zinc-500">
            No guest interactions recorded yet. Scan your restaurant QR code to generate analytics.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="border-b hairline text-[10px] uppercase tracking-[0.2em] text-zinc-500">
                <tr>
                  <th className="pb-3 pl-2 font-semibold">#</th>
                  <th className="pb-3 font-semibold">Dish</th>
                  <th className="pb-3 text-right font-semibold">Card Views</th>
                  <th className="pb-3 text-right font-semibold">3D Opens</th>
                  <th className="pb-3 pr-2 text-right font-semibold">AR Placements</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/60">
                {data.topDishes.map((dish, i) => (
                  <tr key={dish.id} className="transition-colors hover:bg-white/[0.02]">
                    <td className="py-3 pl-2 font-mono text-xs text-zinc-600">{i + 1}</td>
                    <td className="py-3">
                      <div className="flex items-center gap-3">
                        <div className="h-11 w-11 shrink-0 overflow-hidden rounded-xl bg-black">
                          {dish.imageUrl ? (
                            <img src={dish.imageUrl} alt="" className="h-full w-full object-cover" />
                          ) : (
                            <div className="grid h-full place-items-center text-zinc-700">
                              <Box size={16} />
                            </div>
                          )}
                        </div>
                        <span className="font-semibold text-zinc-100">{dish.title}</span>
                      </div>
                    </td>
                    <td className="py-3 text-right font-mono text-zinc-300">
                      {dish.viewsCount.toLocaleString()}
                    </td>
                    <td className="py-3 text-right font-mono text-amber-300">
                      {dish.modelViewsCount.toLocaleString()}
                    </td>
                    <td className="py-3 pr-2 text-right font-mono text-emerald-300">
                      {dish.arLaunchesCount.toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
