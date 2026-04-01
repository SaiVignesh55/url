import React from "react";
import { motion as Motion } from "framer-motion";

const Card = ({ title, desc }) => {
  return (
    <Motion.article
      initial={{ opacity: 0, y: 70 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.2 }}
      transition={{ duration: 0.5 }}
      whileHover={{ y: -5 }}
      className="glass-card p-5 sm:p-6 flex flex-col gap-3 transition-all duration-300 hover:shadow-lg"
    >
      <h3 className="text-white text-xl font-bold">{title}</h3>
      <p className="text-slate-200 text-sm leading-relaxed">{desc}</p>
    </Motion.article>
  );
};

export default Card;