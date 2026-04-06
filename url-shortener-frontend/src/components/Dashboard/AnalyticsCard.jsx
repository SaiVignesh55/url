import React, { useMemo } from "react";

const formatLocalDateKey = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
};

const toLocalDateKey = (rawDate) => {
  if (!rawDate) {
    return null;
  }

  // Prefer stable YYYY-MM-DD extraction when backend already sends date strings.
  const asString = String(rawDate).trim();
  if (asString.length >= 10) {
    const candidate = asString.slice(0, 10);
    if (/^\d{4}-\d{2}-\d{2}$/.test(candidate)) {
      return candidate;
    }
  }

  const parsed = new Date(rawDate);
  if (Number.isNaN(parsed.getTime())) {
    return null;
  }

  return formatLocalDateKey(parsed);
};

const AnalyticsCard = ({ clickData = [] }) => {
  const series = useMemo(() => {
    const countByDate = new Map();

    const normalizedInput = Array.isArray(clickData) ? clickData : [];

    normalizedInput.forEach((item) => {
      const rawDate = item?.clickDate ?? item?.date;
      const key = toLocalDateKey(rawDate);
      if (!key) {
        return;
      }

      const current = countByDate.get(key) || 0;
      const safeCount = Number.isFinite(Number(item?.count)) ? Number(item?.count) : 0;
      countByDate.set(key, current + Math.max(0, safeCount));
    });

    const points = [];
    for (let i = 6; i >= 0; i -= 1) {
      const date = new Date();
      date.setHours(0, 0, 0, 0);
      date.setDate(date.getDate() - i);
      const key = formatLocalDateKey(date);

      points.push({
        date: key,
        label: key.slice(5),
        count: countByDate.get(key) || 0,
      });
    }

    const totalClicks = points.reduce((sum, point) => sum + point.count, 0);
    const todayClicks = points[points.length - 1]?.count || 0;
    const maxCount = Math.max(...points.map((point) => point.count), 0);
    const allZero = points.every((point) => point.count === 0);

    return {
      points,
      totalClicks,
      todayClicks,
      maxCount,
      allZero,
    };
  }, [clickData]);

  return (
    <div className="w-full max-w-5xl mx-auto">
      <div className="group rounded-xl bg-gradient-to-r from-indigo-500/50 via-violet-500/30 to-cyan-500/50 p-[1px] transition-transform duration-300 hover:scale-[1.01]">
        <div className="rounded-xl bg-slate-950 p-6 sm:p-8 shadow-[0_0_35px_rgba(99,102,241,0.15)]">
        <div className="flex items-center justify-between">
          <h3 className="text-white text-xl font-semibold">Link Analytics</h3>
          <div className="flex items-center gap-2 text-sm text-slate-300">
            <span className="h-2.5 w-2.5 rounded-full bg-emerald-400 animate-pulse" />
            Live
          </div>
        </div>

        <div className="mt-6 grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="rounded-lg bg-slate-900/90 p-4">
            <p className="text-[11px] uppercase tracking-wider text-slate-400">Total Clicks</p>
            <p className="text-3xl font-bold text-white mt-1">{series.totalClicks}</p>
          </div>
          <div className="rounded-lg bg-slate-900/90 p-4">
            <p className="text-[11px] uppercase tracking-wider text-slate-400">Today Clicks</p>
            <p className="text-3xl font-bold text-white mt-1">{series.todayClicks}</p>
          </div>
        </div>

        <div className="mt-6 rounded-lg bg-slate-900 p-4 sm:p-5 w-full">
          {series.allZero && (
            <p className="text-sm text-slate-300 mb-3">No clicks yet</p>
          )}
          <div className="h-48 w-full flex items-end justify-between gap-2 sm:gap-3">
            {series.points.map((point) => {
              const heightPercent = series.maxCount === 0
                ? 10
                : Math.max((point.count / series.maxCount) * 100, 10);
              const barHeight = series.maxCount === 0 ? "5px" : `${heightPercent}%`;

              return (
                <div key={point.date} className="flex-1 min-w-0 flex flex-col items-center gap-2">
                  <div className="text-[11px] text-slate-300">{point.count}</div>
                  <div className="w-full h-40 flex items-end">
                    <div
                      className="w-full rounded-md bg-indigo-500 transition-all duration-700 ease-out"
                      style={{ height: barHeight }}
                      title={`${point.date}: ${point.count} clicks`}
                    />
                  </div>
                  <div className="text-[11px] text-slate-400">{point.label}</div>
                </div>
              );
            })}
          </div>
        </div>

        <div className="mt-5 flex items-center justify-between text-sm text-slate-300">
          <span>Last 7 days</span>
          <button
            type="button"
            className="rounded-md border border-slate-700 px-3 py-1 text-xs text-slate-200 hover:bg-slate-800 transition-colors duration-300"
          >
            Clicks only
          </button>
        </div>
      </div>
      </div>
    </div>
  );
};

export default AnalyticsCard;

