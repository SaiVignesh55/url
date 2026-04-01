import React from "react";
import { motion as Motion } from "framer-motion";

const ScreenshotPanel = ({ screenshotUrl }) => {
  return (
    <Motion.section
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.45 }}
      className="glass-card p-6"
    >
      <h3 className="text-base font-semibold text-white">Screenshot Preview</h3>

      {screenshotUrl ? (
        <>
          <div className="mt-4 rounded-lg border border-white/20 overflow-hidden bg-slate-900/50">
            <img
              src={screenshotUrl}
              alt="Scanned website preview"
              className="w-full object-cover"
              loading="lazy"
            />
          </div>

          <a
            href={screenshotUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex mt-3 text-sm font-semibold border border-white/25 rounded-md px-3 py-1.5 text-slate-100 hover:bg-white/10 transition-colors duration-300"
          >
            Open Full Screenshot
          </a>
        </>
      ) : (
        <p className="mt-4 text-sm text-slate-300">No screenshot available for this scan.</p>
      )}
    </Motion.section>
  );
};

export default ScreenshotPanel;

