import React from "react";
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
import { useFetchRegionStats } from "../hooks/useQuery";

ChartJS.register(BarElement, CategoryScale, LinearScale, Legend, Tooltip);

const RegionAnalytics = () => {
  const navigate = useNavigate();
  const { token } = useStoreContext();

  const {
    data: regionStats = [],
    isLoading,
    isFetching,
    isError,
    refetch,
  } = useFetchRegionStats(token, () => {});

  const topRegion = regionStats.length > 0 ? regionStats[0] : null;

  const chartData = {
    labels: regionStats.map((item) => item.region),
    datasets: [
      {
        label: "Visits",
        data: regionStats.map((item) => item.count),
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
          text: "Region",
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
              <h1 className="text-2xl font-serif font-bold text-white">Region Analytics</h1>
              <p className="text-slate-300 text-sm mt-1">
                Analyze visitor distribution by region.
              </p>
            </div>

            <div className="flex gap-2 w-full sm:w-auto">
              <button
                type="button"
                onClick={() => refetch()}
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

          {topRegion && (
            <div className="mt-4 rounded-xl border border-emerald-400/30 bg-emerald-500/10 px-4 py-3 text-emerald-200 text-sm font-semibold">
              Top Region: {topRegion.region} ({topRegion.count} visits)
            </div>
          )}
        </div>

        <div className="glass-card p-5 sm:p-6">
          {(isLoading || isFetching) && (
            <p className="text-slate-200 text-sm">Loading region data...</p>
          )}

          {isError && !isLoading && (
            <p className="text-rose-300 text-sm">Failed to load region data</p>
          )}

          {!isLoading && !isError && regionStats.length === 0 && (
            <p className="text-slate-300 text-sm">No region data available</p>
          )}

          {!isLoading && !isError && regionStats.length > 0 && (
            <div className="space-y-6">
              <div className="h-[320px]">
                <Bar data={chartData} options={chartOptions} />
              </div>

              <div className="overflow-x-auto rounded-xl border border-white/10">
                <table className="min-w-full text-sm text-slate-200">
                  <thead className="bg-white/5 text-slate-100">
                    <tr>
                      <th className="px-4 py-3 text-left font-semibold">Region</th>
                      <th className="px-4 py-3 text-left font-semibold">Visits</th>
                    </tr>
                  </thead>
                  <tbody>
                    {regionStats.map((item) => (
                      <tr key={`${item.region}-${item.count}`} className="border-t border-white/10">
                        <td className="px-4 py-3">{item.region}</td>
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

export default RegionAnalytics;

