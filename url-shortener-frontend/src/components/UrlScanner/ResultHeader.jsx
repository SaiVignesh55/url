import React, { useEffect, useMemo, useState } from "react";
import { motion as Motion } from "framer-motion";

const getStatusClasses = (status) => {
  if (status === "SAFE") {
    return {
      chip: "bg-emerald-500/20 text-emerald-200 border-emerald-300/40",
      bar: "from-emerald-400 to-emerald-500",
      glow: "shadow-glowGreen",
      ring: "ring-emerald-300/30",
    };
  }

  if (status?.startsWith("SUSPICIOUS")) {
    return {
      chip: "bg-amber-500/20 text-amber-100 border-amber-300/40",
      bar: "from-amber-400 to-amber-500",
      glow: "shadow-glowAmber",
      ring: "ring-amber-300/30",
    };
  }

  if (status === "DANGEROUS") {
    return {
      chip: "bg-rose-500/20 text-rose-100 border-rose-300/40",
      bar: "from-rose-500 to-red-600",
      glow: "shadow-glowRed",
      ring: "ring-rose-300/30",
    };
  }

  return {
    chip: "bg-slate-500/20 text-slate-200 border-slate-300/30",
    bar: "from-slate-400 to-slate-500",
    glow: "",
    ring: "ring-slate-300/20",
  };
};

const ResultHeader = ({ status, message, scannedUrl, finalScore, onCopyScannedUrl, copied }) => {
  const classes = getStatusClasses(status);
  const [displayScore, setDisplayScore] = useState(0);

  useEffect(() => {
    const target = Math.max(0, Math.min(finalScore || 0, 100));
    let current = 0;
    const step = Math.max(1, Math.ceil(target / 25));
    const timer = setInterval(() => {
      current += step;
      if (current >= target) {
        setDisplayScore(target);
        clearInterval(timer);
        return;
      }
      setDisplayScore(current);
    }, 20);

    return () => clearInterval(timer);
  }, [finalScore]);

  const scorePercent = useMemo(() => Math.max(0, Math.min(displayScore, 100)), [displayScore]);

  return (
    <Motion.section
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.45 }}
      className={`glass-card p-6 ring-1 ${classes.ring} ${classes.glow}`}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs uppercase tracking-wider text-slate-300 font-semibold">Scan Verdict</p>
          <div className={`mt-2 inline-flex items-center border px-3 py-1 rounded-full text-sm font-semibold ${classes.chip}`}>
            {status || "N/A"}
          </div>
        </div>

        <div className="text-right">
          <p className="text-xs uppercase tracking-wider text-slate-300 font-semibold">Final Score</p>
          <p className="text-3xl font-bold text-white">
            {scorePercent}
            <span className="text-base text-slate-300">/100</span>
          </p>
        </div>
      </div>

      <div className="mt-4 h-3 w-full rounded-full bg-slate-800/80 overflow-hidden border border-white/10">
        <Motion.div
          className={`h-full bg-gradient-to-r ${classes.bar}`}
          initial={{ width: 0 }}
          animate={{ width: `${scorePercent}%` }}
          transition={{ duration: 0.7, ease: "easeOut" }}
        />
      </div>

      <p className="mt-4 text-sm text-slate-200">
        <span className="font-semibold text-white">Message:</span> {message || "No message"}
      </p>

      <div className="mt-3 flex flex-col gap-2">
        <p className="text-xs uppercase tracking-wider text-slate-300 font-semibold">Scanned URL</p>
        <p className="text-sm text-slate-100 break-all">{scannedUrl || "-"}</p>
        {scannedUrl && (
          <div>
            <Motion.button
              type="button"
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={onCopyScannedUrl}
              className="text-xs font-semibold border border-white/25 rounded-md px-3 py-1.5 text-slate-100 hover:bg-white/10"
            >
              {copied ? "Copied" : "Copy URL"}
            </Motion.button>
          </div>
        )}
      </div>
    </Motion.section>
  );
};

export default ResultHeader;

