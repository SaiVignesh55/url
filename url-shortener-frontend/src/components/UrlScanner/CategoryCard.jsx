import React from "react";
import { motion as Motion } from "framer-motion";

const getScoreColor = (score) => {
  if (score <= 20) {
    return {
      text: "text-emerald-300",
      bg: "bg-emerald-500",
      chip: "bg-emerald-500/20 text-emerald-200 border-emerald-300/40",
    };
  }

  if (score <= 50) {
    return {
      text: "text-amber-300",
      bg: "bg-amber-500",
      chip: "bg-amber-500/20 text-amber-200 border-amber-300/40",
    };
  }

  return {
    text: "text-red-300",
    bg: "bg-red-600",
    chip: "bg-red-500/20 text-red-200 border-red-300/40",
  };
};

const deriveLabel = (score) => {
  if (score <= 20) return "SAFE";
  if (score <= 35) return "LOW";
  if (score <= 50) return "MEDIUM";
  return "HIGH";
};

const CategoryCard = ({ title, score, label }) => {
  const safeScore = Number.isFinite(score) ? Math.max(0, Math.min(score, 100)) : 0;
  const derivedLabel = label || deriveLabel(safeScore);
  const colors = getScoreColor(safeScore);

  return (
    <Motion.article
      whileHover={{ y: -5 }}
      transition={{ duration: 0.3 }}
      className="glass-card p-4 transition-all duration-300 hover:shadow-lg"
    >
      <div className="flex items-start justify-between gap-2">
        <h4 className="text-sm font-semibold text-white">{title}</h4>
        <span className={`inline-flex border rounded-full px-2 py-0.5 text-[11px] font-semibold ${colors.chip}`}>
          {derivedLabel}
        </span>
      </div>

      <p className={`mt-3 text-2xl font-bold ${colors.text}`}>{safeScore}<span className="text-sm text-slate-300">/100</span></p>

      <div className="mt-3 h-2 w-full rounded-full bg-slate-800/80 overflow-hidden">
        <div className={`h-full transition-all duration-500 ${colors.bg}`} style={{ width: `${safeScore}%` }} />
      </div>
    </Motion.article>
  );
};

export default CategoryCard;

