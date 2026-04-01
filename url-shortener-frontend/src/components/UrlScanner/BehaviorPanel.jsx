import React, { useMemo, useState } from "react";
import { motion as Motion } from "framer-motion";

const BehaviorPanel = ({ finalUrl, redirectChain, totalRequests, domains, checksPerformed, onCopyFinalUrl, copiedFinal }) => {
  const [showAllDomains, setShowAllDomains] = useState(false);

  const visibleDomains = useMemo(() => {
    if (!Array.isArray(domains)) return [];
    if (showAllDomains) return domains;
    return domains.slice(0, 5);
  }, [domains, showAllDomains]);

  const hasMoreDomains = Array.isArray(domains) && domains.length > 5;

  return (
    <Motion.section
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.45 }}
      className="glass-card p-6"
    >
      <h3 className="text-base font-semibold text-white">Behavior Analysis</h3>

      <div className="mt-4 grid gap-5 md:grid-cols-2">
        <div>
          <p className="text-xs uppercase tracking-wider text-slate-300 font-semibold">Final URL</p>
          <p className="mt-2 text-sm text-slate-100 break-all">{finalUrl || "-"}</p>
          {finalUrl && (
            <button
              type="button"
              onClick={onCopyFinalUrl}
              className="mt-2 text-xs font-semibold border border-white/25 rounded-md px-3 py-1.5 text-slate-100 hover:bg-white/10 transition-colors duration-300"
            >
              {copiedFinal ? "Copied" : "Copy Final URL"}
            </button>
          )}
        </div>

        <div>
          <p className="text-xs uppercase tracking-wider text-slate-300 font-semibold">Total Requests</p>
          <p className="mt-2 text-2xl font-bold text-white">{totalRequests ?? 0}</p>
        </div>
      </div>

      <div className="mt-5">
        <p className="text-xs uppercase tracking-wider text-slate-300 font-semibold">Redirect Chain</p>
        {Array.isArray(redirectChain) && redirectChain.length > 1 ? (
          <ol className="mt-2 space-y-2">
            {redirectChain.map((item, index) => (
              <li key={`${item}-${index}`} className="rounded-md bg-white/10 border border-white/20 p-2 text-sm text-slate-100 break-all">
                <span className="mr-2 font-semibold text-slate-300">{index + 1}.</span>
                {item}
              </li>
            ))}
          </ol>
        ) : (
          <p className="mt-2 text-sm text-slate-300">No redirects detected.</p>
        )}
      </div>

      <div className="mt-5">
        <p className="text-xs uppercase tracking-wider text-slate-300 font-semibold">Domains Contacted</p>
        {visibleDomains.length > 0 ? (
          <>
            <ul className="mt-2 flex flex-wrap gap-2">
              {visibleDomains.map((domain, index) => (
                <li key={`${domain}-${index}`} className="text-xs px-2 py-1 rounded-full border border-white/20 bg-white/10 text-slate-100">
                  {domain}
                </li>
              ))}
            </ul>
            {hasMoreDomains && (
              <button
                type="button"
                onClick={() => setShowAllDomains((prev) => !prev)}
                className="mt-2 text-xs font-semibold text-blue-300 hover:text-blue-200"
              >
                {showAllDomains ? "Show less" : `Show more (${domains.length - 5} more)`}
              </button>
            )}
          </>
        ) : (
          <p className="mt-2 text-sm text-slate-300">No external domains recorded.</p>
        )}
      </div>

      {Array.isArray(checksPerformed) && checksPerformed.length > 0 && (
        <div className="mt-5">
          <p className="text-xs uppercase tracking-wider text-slate-300 font-semibold">Checks Performed</p>
          <ul className="mt-2 list-disc ml-5 text-sm text-slate-200 space-y-1">
            {checksPerformed.map((item, index) => (
              <li key={`${item}-${index}`}>{item}</li>
            ))}
          </ul>
        </div>
      )}
    </Motion.section>
  );
};

export default BehaviorPanel;

