import React, { Suspense, lazy } from "react";
import { motion as Motion } from "framer-motion";

const Hero3DObject = lazy(() => import("./Hero3DObject"));

const HeroSection = ({ onPrimaryClick, onSecondaryClick }) => {
  return (
    <section className="relative overflow-hidden rounded-2xl border border-white/20 bg-white/5 backdrop-blur-xl min-h-[540px] sm:min-h-[580px] px-5 sm:px-8 lg:px-12 py-10">
      <div className="absolute -top-16 -left-12 h-64 w-64 rounded-full bg-blue-500/30 blur-3xl" />
      <div className="absolute -bottom-16 -right-12 h-72 w-72 rounded-full bg-purple-500/30 blur-3xl" />
      <div className="absolute inset-0 bg-gradient-to-b from-transparent via-slate-900/20 to-slate-950/50" />

      <div className="relative z-10 mx-auto max-w-6xl h-full flex flex-col justify-center">
        <Motion.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="max-w-3xl"
        >
          <h1 className="font-black leading-tight tracking-tight text-5xl sm:text-6xl lg:text-7xl bg-gradient-to-r from-blue-300 via-cyan-200 to-purple-300 bg-clip-text text-transparent">
            Scan. Shorten. Secure.
          </h1>
          <p className="mt-3 text-sm sm:text-base text-slate-200">
            Your all-in-one URL intelligence platform.
          </p>

          <Motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, delay: 0.2 }}
            className="mt-6 flex flex-wrap gap-3"
          >
            <Motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={onPrimaryClick}
              className="btn-gradient min-w-40"
            >
              Start Scanning
            </Motion.button>
            <Motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={onSecondaryClick}
              className="min-w-40 rounded-xl px-5 py-2.5 border border-white/30 text-slate-100 font-semibold bg-white/5 hover:bg-white/10 transition-all duration-300"
            >
              Create Short Link
            </Motion.button>
          </Motion.div>
        </Motion.div>

        <div className="hidden lg:block absolute right-0 top-1/2 -translate-y-1/2 w-[55%] max-w-[760px]">
          <div className="absolute inset-0 m-auto h-64 w-64 rounded-full bg-blue-500/20 blur-3xl" />
          <Suspense fallback={<div className="h-[500px] glass-card animate-pulse" />}>
            <Hero3DObject className="h-[500px] w-full" />
          </Suspense>
        </div>

        <div className="lg:hidden mt-8">
          <Suspense fallback={<div className="h-[300px] glass-card animate-pulse" />}>
            <Hero3DObject className="h-[300px] w-full" />
          </Suspense>
        </div>
      </div>
    </section>
  );
};

export default HeroSection;

