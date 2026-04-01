import React, { useState } from "react";
import { motion as Motion } from "framer-motion";
import { FaChartLine, FaLink, FaMousePointer, FaPlus } from "react-icons/fa";
import Graph from "./Graph";
import { useStoreContext } from "../../contextApi/ContextApi";
import { useFetchMyShortUrls, useFetchTotalClicks } from "../../hooks/useQuery";
import ShortenPopUp from "./ShortenPopUp";
import ShortenUrlList from "./ShortenUrlList";
import { useNavigate } from "react-router-dom";
import Loader from "../Loader";

const DashboardLayout = () => {
  const { token } = useStoreContext();
  const navigate = useNavigate();
  const [shortenPopUp, setShortenPopUp] = useState(false);

  const { isLoading, data: myShortenUrls, refetch } = useFetchMyShortUrls(token, onError);
  const { isLoading: loader, data: totalClicks } = useFetchTotalClicks(token, onError);

  function onError() {
    navigate("/error");
  }

  const totalLinks = myShortenUrls?.length || 0;
  const totalEngagements = totalClicks?.reduce((sum, item) => sum + (item.count || 0), 0) || 0;

  return (
    <div className="lg:px-14 sm:px-8 px-4 min-h-[calc(100vh-64px)] py-8">
      {loader ? (
        <Loader />
      ) : (
        <div className="lg:w-[92%] w-full mx-auto py-6 space-y-5">
          <Motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5 }}
            className="grid md:grid-cols-3 grid-cols-1 gap-4"
          >
            <div className="glass-card p-5 flex items-center gap-3 hover:-translate-y-1 transition-all duration-300">
              <FaLink className="text-blue-300 text-xl" />
              <div>
                <p className="text-xs text-slate-300 uppercase tracking-wider">Total Links</p>
                <p className="text-2xl text-white font-bold">{totalLinks}</p>
              </div>
            </div>
            <div className="glass-card p-5 flex items-center gap-3 hover:-translate-y-1 transition-all duration-300">
              <FaMousePointer className="text-purple-300 text-xl" />
              <div>
                <p className="text-xs text-slate-300 uppercase tracking-wider">Total Clicks</p>
                <p className="text-2xl text-white font-bold">{totalEngagements}</p>
              </div>
            </div>
            <div className="glass-card p-5 flex items-center gap-3 hover:-translate-y-1 transition-all duration-300">
              <FaChartLine className="text-cyan-300 text-xl" />
              <div>
                <p className="text-xs text-slate-300 uppercase tracking-wider">Analytics</p>
                <p className="text-sm text-slate-100 font-semibold">Live engagement snapshot</p>
              </div>
            </div>
          </Motion.div>

          <div className="glass-card p-5 sm:p-6 relative min-h-[390px]">
            {totalClicks.length === 0 && (
              <div className="absolute flex flex-col justify-center sm:items-center items-end w-full left-0 top-0 bottom-0 right-0 m-auto">
                <h1 className="text-white font-serif sm:text-2xl text-[18px] font-bold mb-1">
                  No Data For This Time Period
                </h1>
                <h3 className="sm:w-96 w-[90%] sm:ml-0 pl-6 text-center sm:text-lg text-sm text-slate-300">
                  Share your short link to view where your engagements are coming from
                </h3>
              </div>
            )}
            <Graph graphData={totalClicks} />
          </div>

          <div className="py-2 sm:text-end text-center">
            <Motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              className="btn-gradient inline-flex items-center gap-2"
              onClick={() => setShortenPopUp(true)}
            >
              <FaPlus className="text-xs" />
              Create a New Short URL
            </Motion.button>
          </div>

          <div>
            {!isLoading && myShortenUrls.length === 0 ? (
              <div className="flex justify-center pt-10">
                <div className="glass-card py-6 sm:px-8 px-5 text-center">
                  <h1 className="text-white font-montserrat sm:text-[18px] text-[14px] font-semibold mb-2">
                    You haven't created any short link yet
                  </h1>
                  <FaLink className="text-blue-300 sm:text-xl text-sm mx-auto" />
                </div>
              </div>
            ) : (
              <ShortenUrlList data={myShortenUrls} />
            )}
          </div>
        </div>
      )}

      <ShortenPopUp refetch={refetch} open={shortenPopUp} setOpen={setShortenPopUp} />
    </div>
  );
};

export default DashboardLayout;

