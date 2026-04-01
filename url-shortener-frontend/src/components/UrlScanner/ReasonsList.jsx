import React from "react";
import { motion as Motion } from "framer-motion";

const ReasonsList = ({ reasons }) => {
  return (
    <Motion.section
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.45 }}
      className="glass-card p-6"
    >
      <h3 className="text-base font-semibold text-white">Reasons</h3>

      {Array.isArray(reasons) && reasons.length > 0 ? (
        <ul className="mt-4 space-y-2">
          {reasons.map((reason, index) => (
            <li key={`${reason}-${index}`} className="flex items-start gap-2 text-sm text-slate-100">
              <span className="text-emerald-600 font-bold">&#10003;</span>
              <span>{reason}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-4 text-sm text-slate-300">No additional reasons were returned by the scanner.</p>
      )}
    </Motion.section>
  );
};

export default ReasonsList;

