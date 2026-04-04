import React, { useEffect, useState } from "react";
import { motion as Motion } from "framer-motion";

const ScreenshotPanel = ({ screenshotUrl, resultUrl, isProcessing = false }) => {
  const MAX_RETRIES = 5;
  const [retry, setRetry] = useState(0);
  const [failed, setFailed] = useState(false);
  const [isImageLoading, setIsImageLoading] = useState(Boolean(screenshotUrl));
  const [cacheBuster, setCacheBuster] = useState(() => Date.now());

  useEffect(() => {
    if (screenshotUrl && !failed) {
      console.log(`Loading screenshot attempt ${retry + 1}`);
    }
  }, [retry, screenshotUrl, failed]);

  const imageSrc = screenshotUrl ? `${screenshotUrl}?t=${cacheBuster}` : "";

  const openUrl = resultUrl || "#";

  const onImageError = () => {
    if (retry < MAX_RETRIES) {
      console.log("Retrying screenshot");
      setIsImageLoading(true);
      setTimeout(() => {
        setRetry((value) => value + 1);
        setCacheBuster(Date.now());
      }, 3000);
      return;
    }
    console.log("Final failure");
    setFailed(true);
    setIsImageLoading(false);
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

      {screenshotUrl && !failed ? (
        <>
          <div className="mt-4 rounded-lg border border-white/20 overflow-hidden bg-slate-900/50">
            <img
              key={retry}
              src={imageSrc}
              alt="Screenshot"
              style={{ width: "100%", borderRadius: "10px" }}
              className="w-full object-cover"
              loading="lazy"
              onError={onImageError}
              onLoad={() => setIsImageLoading(false)}
            />
          </div>

          {isImageLoading && <p className="mt-3 text-sm text-slate-300">Loading screenshot...</p>}

          <a
            href={openUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex mt-3 text-sm font-semibold border border-white/25 rounded-md px-3 py-1.5 text-slate-100 hover:bg-white/10 transition-colors duration-300"
          >
            Open Full Screenshot
          </a>
        </>
      ) : isProcessing ? (
        <>
          <p className="mt-4 text-sm text-slate-300">Loading screenshot...</p>

          <a
            href={openUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex mt-3 text-sm font-semibold border border-white/25 rounded-md px-3 py-1.5 text-slate-100 hover:bg-white/10 transition-colors duration-300"
          >
            Open Full Screenshot
          </a>
        </>
      ) : failed ? (
        <>
          <p className="mt-4 text-sm text-slate-300">Screenshot not available yet</p>

          <a
            href={openUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex mt-3 text-sm font-semibold border border-white/25 rounded-md px-3 py-1.5 text-slate-100 hover:bg-white/10 transition-colors duration-300"
          >
            Open Full Screenshot
          </a>
        </>
      ) : (
        <>
          <p className="mt-4 text-sm text-slate-300">Screenshot not available yet</p>

          <a
            href={openUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex mt-3 text-sm font-semibold border border-white/25 rounded-md px-3 py-1.5 text-slate-100 hover:bg-white/10 transition-colors duration-300"
          >
            Open Full Screenshot
          </a>
        </>
      )}
    </Motion.section>
  );
};

export default ScreenshotPanel;

