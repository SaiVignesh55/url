import React, { useEffect, useMemo, useState } from "react";
import { motion as Motion } from "framer-motion";

const ScreenshotPanel = ({ screenshotUrl, isProcessing = false }) => {
  const MAX_RETRIES = 3;
  const [retryCount, setRetryCount] = useState(0);
  const [isRetrying, setIsRetrying] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    setRetryCount(0);
    setIsRetrying(false);
    setLoadFailed(false);
  }, [screenshotUrl]);

  useEffect(() => {
    if (!isRetrying) return undefined;

    const timer = setTimeout(() => {
      setRetryCount((prev) => prev + 1);
      setIsRetrying(false);
    }, 1800);

    return () => clearTimeout(timer);
  }, [isRetrying]);

  const imageSrc = useMemo(() => {
    if (!screenshotUrl) return "";
    const token = `retry=${retryCount}`;
    return screenshotUrl.includes("?") ? `${screenshotUrl}&${token}` : `${screenshotUrl}?${token}`;
  }, [screenshotUrl, retryCount]);

  const onImageError = () => {
    if (retryCount < MAX_RETRIES) {
      setIsRetrying(true);
      return;
    }
    setLoadFailed(true);
  };

  return (
    <Motion.section
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.45 }}
      className="glass-card p-6"
    >
      <h3 className="text-base font-semibold text-white">Screenshot Preview</h3>

      {screenshotUrl && !loadFailed ? (
        <>
          <div className="mt-4 rounded-lg border border-white/20 overflow-hidden bg-slate-900/50">
            {screenshotUrl && (
              <img
                src={imageSrc}
                alt="Website Screenshot"
                style={{ width: "100%", borderRadius: "10px" }}
                className="w-full object-cover"
                loading="lazy"
                onError={onImageError}
                onLoad={() => {
                  setIsRetrying(false);
                  setLoadFailed(false);
                }}
              />
            )}
          </div>

          {isRetrying && <p className="mt-3 text-sm text-slate-300">Loading screenshot...</p>}

          <a
            href={imageSrc}
            target="_blank"
            rel="noreferrer"
            className="inline-flex mt-3 text-sm font-semibold border border-white/25 rounded-md px-3 py-1.5 text-slate-100 hover:bg-white/10 transition-colors duration-300"
          >
            Open Full Screenshot
          </a>
        </>
      ) : isProcessing ? (
        <p className="mt-4 text-sm text-slate-300">Screenshot processing in urlscan...</p>
      ) : screenshotUrl && loadFailed ? (
        <p className="mt-4 text-sm text-slate-300">Loading screenshot...</p>
      ) : (
        <p className="mt-4 text-sm text-slate-300">No screenshot available for this scan.</p>
      )}
    </Motion.section>
  );
};

export default ScreenshotPanel;

