import { useNavigate } from "react-router-dom";
import React, { Suspense, lazy } from "react";
import { motion as Motion } from "framer-motion";

import Card from "./Card";
import { useStoreContext } from "../contextApi/ContextApi";

const Hero3DObject = lazy(() => import("./Hero3DObject"));

const containerVariants = {
  hidden: { opacity: 0, y: 24 },
  show: {
    opacity: 1,
    y: 0,
    transition: {
      duration: 0.6,
      staggerChildren: 0.12,
    },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 24 },
  show: { opacity: 1, y: 0, transition: { duration: 0.5 } },
};

const LandingPage = () => {
  const navigate = useNavigate();
  const { token } = useStoreContext();

  const dashBoardNavigateHandler = () => {
    if (token) {
      navigate("/dashboard");
      return;
    }

    navigate("/login");
  };

  return (
    <Motion.div
      initial="hidden"
      animate="show"
      variants={containerVariants}
      className="min-h-[calc(100vh-64px)] lg:px-14 sm:px-8 px-4 py-8 sm:py-10"
    >
      <section className="relative h-[78vh] min-h-[520px] overflow-hidden rounded-[26px] border border-white/20 bg-gradient-to-r from-[#081532] to-[#1b1844]">
        <div className="absolute inset-0 z-0">
          <Suspense fallback={<div className="h-full w-full bg-slate-900/40" />}>
            <Hero3DObject className="h-full w-full" />
          </Suspense>
        </div>

        <div className="absolute inset-0 z-10 bg-gradient-to-r from-[#0f172a]/92 via-[#0f172a]/70 to-transparent" />

        <div className="relative z-20 h-full flex items-center px-6 sm:px-10 lg:px-14">
          <Motion.div variants={itemVariants} className="max-w-2xl">
            <h1 className="text-white font-roboto font-bold text-5xl sm:text-6xl lg:text-7xl leading-[1.04] tracking-tight">
              Scan. Shorten. Secure.
            </h1>

            <p className="mt-4 text-slate-300 text-base sm:text-lg">
              Your all-in-one URL intelligence platform.
            </p>

            <div className="flex flex-wrap items-center gap-3 mt-8">
              <Motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={dashBoardNavigateHandler}
                className="btn-gradient min-w-[11rem]"
              >
                Start Scanning
              </Motion.button>
              <Motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={dashBoardNavigateHandler}
                className="min-w-[11rem] rounded-xl px-5 py-2.5 border border-white/30 text-slate-100 font-semibold bg-white/10 hover:bg-white/15 transition-all duration-300"
              >
                Create Short Link
              </Motion.button>
            </div>
          </Motion.div>
        </div>
      </section>

      <Motion.section
        initial={{ opacity: 0, y: 35 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true, amount: 0.2 }}
        transition={{ duration: 0.6 }}
        className="pt-12"
      >
        <p className="text-white font-roboto font-bold lg:w-[60%] md:w-[70%] sm:w-[80%] mx-auto text-3xl text-center">
          Trusted by individuals and teams at the world best companies
        </p>

        <Motion.div
          variants={containerVariants}
          initial="hidden"
          whileInView="show"
          viewport={{ once: true, amount: 0.15 }}
          className="pt-5 pb-7 grid lg:gap-7 gap-4 xl:grid-cols-4 lg:grid-cols-3 sm:grid-cols-2 grid-cols-1 mt-4"
        >
          <Card
            title="Simple URL Shortening"
            desc="Create short, memorable URLs in seconds with a polished workflow and no friction."
          />
          <Card
            title="Powerful Analytics"
            desc="Track clicks, traffic origins, and engagement trends to improve decisions quickly."
          />
          <Card
            title="Enhanced Security"
            desc="Scan links with multi-layer checks so suspicious destinations are flagged early."
          />
          <Card
            title="Fast and Reliable"
            desc="Built on performant infrastructure for quick redirects and consistent uptime."
          />
        </Motion.div>
      </Motion.section>
    </Motion.div>
  );
};

export default LandingPage;