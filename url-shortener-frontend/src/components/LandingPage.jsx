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
      <div className="grid lg:grid-cols-2 gap-8 lg:gap-10 items-center">
        <Motion.div variants={itemVariants} className="glass-card p-6 sm:p-8">
          <h1 className="font-bold font-roboto text-white md:text-5xl sm:text-4xl text-3xl md:leading-[56px] sm:leading-[46px] leading-10">
            Linklytics Makes URL Security and Sharing Feel Effortless.
          </h1>
          <p className="text-slate-200 text-sm sm:text-base mt-5 leading-relaxed">
            Generate short links, scan threat signals, and monitor behavior from
            one clean dashboard. Designed for speed, clarity, and secure sharing
            at scale.
          </p>

          <div className="flex flex-wrap items-center gap-3 mt-6">
            <Motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={dashBoardNavigateHandler}
              className="btn-gradient min-w-40"
            >
              Manage Links
            </Motion.button>
            <Motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={dashBoardNavigateHandler}
              className="min-w-40 rounded-xl px-5 py-2.5 border border-blue-300/60 text-blue-100 font-semibold hover:bg-blue-500/15 transition-all duration-300"
            >
              Create Short Link
            </Motion.button>
          </div>
        </Motion.div>

        <Motion.div
          variants={itemVariants}
          initial={{ opacity: 0, x: 40 }}
          whileInView={{ opacity: 1, x: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.6 }}
          className="w-full"
        >
          <Suspense
            fallback={<div className="h-[320px] sm:h-[380px] w-full glass-card animate-pulse" />}
          >
            <Hero3DObject />
          </Suspense>
        </Motion.div>
      </div>

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