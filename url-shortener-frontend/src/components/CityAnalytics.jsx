import React, { useMemo, useState } from "react";
import { motion as Motion } from "framer-motion";
import { Bar } from "react-chartjs-2";
import {
  Chart as ChartJS,
  BarElement,
  CategoryScale,
  LinearScale,
  Legend,
  Tooltip,
} from "chart.js";
import { useNavigate } from "react-router-dom";
import { useStoreContext } from "../contextApi/ContextApi";
import { useFetchCityStatsByShortCode, useFetchMyLinks } from "../hooks/useQuery";

ChartJS.register(BarElement, CategoryScale, LinearScale, Legend, Tooltip);

const CityAnalytics = () => {
  const navigate = useNavigate();
  const { token } = useStoreContext();
  const [selectedShortCode, setSelectedShortCode] = useState("");

  const {
    data: links = [],
    isLoading: isLinksLoading,
    isError: isLinksError,
    refetch: refetchLinks,
  } = useFetchMyLinks(token, () => {});


  const activeShortCode = selectedShortCode || links[0]?.shortCode || "";

  const {
    data: cityStats = [],
    isLoading: isCityLoading,
    isFetching: isCityFetching,
    isError: isCityError,
    error: cityError,
    refetch: refetchCity,
  } = useFetchCityStatsByShortCode(token, activeShortCode, () => {});

  const cityErrorMessage =
    cityError?.response?.data?.message ||
    cityError?.response?.data?.error ||
    cityError?.message ||
    "Failed to load city data";

  const selectedLink = useMemo(
    () => links.find((item) => item.shortCode === activeShortCode) || null,
    [links, activeShortCode]
  );

  const topCity = cityStats.length > 0 ? cityStats[0] : null;

  const isLoading = isLinksLoading || isCityLoading || isCityFetching;

  const selectedDomain = useMemo(() => {
    if (!selectedLink?.originalUrl) {
      return "";
    }

    try {
      const url = new URL(selectedLink.originalUrl);
      return url.hostname.replace(/^www\./, "");
    } catch {
      return selectedLink.originalUrl;
    }
  }, [selectedLink]);

  const chartData = {
    labels: cityStats.map((item) => item.city),
    datasets: [
      {
        label: "Visits",
        data: cityStats.map((item) => item.count),
        backgroundColor: "rgba(96, 165, 250, 0.8)",
        borderColor: "rgba(59, 130, 246, 1)",
        borderWidth: 1,
        borderRadius: 6,
      },
    ],
  };

  const chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        labels: {
          color: "#e2e8f0",
        },
      },
    },
    scales: {
      x: {
        ticks: {
          color: "#cbd5e1",
        },
        grid: {
          color: "rgba(148, 163, 184, 0.15)",
        },
        title: {
          display: true,
          text: "City",
          color: "#e2e8f0",
        },
      },
      y: {
        beginAtZero: true,
        ticks: {
          precision: 0,
          color: "#cbd5e1",
        },
        grid: {
          color: "rgba(148, 163, 184, 0.15)",
        },
        title: {
          display: true,
          text: "Visits",
          color: "#e2e8f0",
        },
      },
    },
  };

  const handleRefresh = () => {
    refetchLinks();
    if (activeShortCode) {
      refetchCity();
    }
  };

  return (
    <Motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45 }}
      className="min-h-[calc(100vh-64px)] lg:px-14 sm:px-8 px-4 py-8"
    >
      <div className="max-w-6xl mx-auto space-y-5">
        <div className="glass-card p-5 sm:p-6">
          <div className="flex flex-wrap justify-between items-center gap-3">
            <div>
              <h1 className="text-2xl font-serif font-bold text-white">City Analytics</h1>
              <p className="text-slate-300 text-sm mt-1">
                Analyze visitor distribution per shortened link.
              </p>
            </div>

            <div className="flex gap-2 w-full sm:w-auto">
              <button
                type="button"
                onClick={handleRefresh}
                className="flex-1 sm:flex-none rounded-xl border border-white/20 px-4 py-2 text-sm font-semibold text-slate-100 hover:bg-white/10 transition-colors"
              >
                Refresh Data
              </button>
              <button
                type="button"
                onClick={() => navigate("/dashboard")}
                className="flex-1 sm:flex-none rounded-xl border border-white/20 px-4 py-2 text-sm font-semibold text-slate-100 hover:bg-white/10 transition-colors"
              >
                Dashboard
              </button>
            </div>
          </div>

          <div className="mt-5 grid md:grid-cols-[1fr_auto] gap-3 items-end">
            <div>
              <label htmlFor="link-selector" className="block text-xs uppercase tracking-wide text-slate-300 mb-2">
                Select Link
              </label>
              <select
                id="link-selector"
                value={activeShortCode}
                onChange={(event) => {
                  const nextShortCode = event.target.value;
                  console.log("selected shortCode:", nextShortCode);
                  setSelectedShortCode(nextShortCode);
                }}
                className="w-full rounded-xl border border-white/15 bg-slate-900/70 px-3 py-2 text-sm text-slate-100 outline-none focus:border-blue-400"
              >
                {links.map((item) => {
                  const optionLabel = `${item.shortCode} (${(() => {
                    try {
                      return new URL(item.originalUrl).hostname.replace(/^www\./, "");
                    } catch {
                      return item.originalUrl;
                    }
                  })()})`;

                  return (
                    <option key={item.shortCode} value={item.shortCode}>
                      {optionLabel}
                    </option>
                  );
                })}
              </select>
            </div>

            {activeShortCode && (
              <div className="text-xs text-slate-300 rounded-xl border border-white/10 bg-white/5 px-3 py-2">
                Active: <span className="text-white font-semibold">{activeShortCode}</span>
              </div>
            )}
          </div>

          {topCity && (
            <div className="mt-4 rounded-xl border border-emerald-400/30 bg-emerald-500/10 px-4 py-3 text-emerald-200 text-sm font-semibold">
              Top City: {topCity.city} ({topCity.count} visits)
            </div>
          )}
        </div>

        <div className="glass-card p-5 sm:p-6">
          {isLoading && (
            <p className="text-slate-200 text-sm">Loading city analytics...</p>
          )}

          {isLinksError && !isLoading && (
            <p className="text-rose-300 text-sm">Failed to load links</p>
          )}

          {isCityError && !isLoading && (
            <p className="text-rose-300 text-sm">{cityErrorMessage}</p>
          )}

          {!isLoading && !isCityError && !activeShortCode && (
            <p className="text-slate-300 text-sm">No links available for analytics</p>
          )}

          {!isLoading && !isCityError && activeShortCode && cityStats.length === 0 && (
            <p className="text-slate-300 text-sm">No analytics available for this link</p>
          )}

          {!isLoading && !isCityError && cityStats.length > 0 && (
            <div className="space-y-6">
              {selectedDomain && (
                <p className="text-xs text-slate-300">
                  Showing analytics for <span className="text-white font-semibold">{activeShortCode}</span> ({selectedDomain})
                </p>
              )}

              <div className="h-[320px]">
                <Bar data={chartData} options={chartOptions} />
              </div>

              <div className="overflow-x-auto rounded-xl border border-white/10">
                <table className="min-w-full text-sm text-slate-200">
                  <thead className="bg-white/5 text-slate-100">
                    <tr>
                      <th className="px-4 py-3 text-left font-semibold">City</th>
                      <th className="px-4 py-3 text-left font-semibold">Visits</th>
                    </tr>
                  </thead>
                  <tbody>
                    {cityStats.map((item) => (
                      <tr key={`${item.city}-${item.count}`} className="border-t border-white/10">
                        <td className="px-4 py-3">{item.city}</td>
                        <td className="px-4 py-3">{item.count}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      </div>
    </Motion.div>
  );
};

export default CityAnalytics;



