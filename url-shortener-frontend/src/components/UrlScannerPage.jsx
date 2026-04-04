import React, { useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { motion as Motion } from "framer-motion";
import ResultHeader from "./UrlScanner/ResultHeader";
import CategoryCard from "./UrlScanner/CategoryCard";
import BehaviorPanel from "./UrlScanner/BehaviorPanel";
import ScreenshotPanel from "./UrlScanner/ScreenshotPanel";
import ReasonsList from "./UrlScanner/ReasonsList";
import { scannerApi } from "../api/api";

const POLL_INTERVAL_MS = 2000;
const MAX_POLL_ATTEMPTS = 10;

const UrlScannerPage = () => {
  const navigate = useNavigate();
  const [url, setUrl] = useState("");
  const [scanId, setScanId] = useState("");
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [pollAttempt, setPollAttempt] = useState(0);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [copied, setCopied] = useState(false);
  const [copiedFinal, setCopiedFinal] = useState(false);
  const backendBaseUrl = import.meta.env.VITE_BACKEND_URL || "http://localhost:9000";

  const pollIntervalRef = useRef(null);
  const elapsedTimerRef = useRef(null);

  const clearPollingTimers = () => {
    if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
      pollIntervalRef.current = null;
    }
    if (elapsedTimerRef.current) {
      clearInterval(elapsedTimerRef.current);
      elapsedTimerRef.current = null;
    }
  };

  const clearAll = () => {
    clearPollingTimers();
    setUrl("");
    setScanId("");
    setResult(null);
    setError("");
    setLoading(false);
    setPollAttempt(0);
    setElapsedSeconds(0);
    setCopied(false);
    setCopiedFinal(false);
  };

  useEffect(() => {
    return () => {
      clearPollingTimers();
    };
  }, []);

  useEffect(() => {
    if (!loading) {
      return;
    }

    elapsedTimerRef.current = setInterval(() => {
      setElapsedSeconds((prev) => prev + 1);
    }, 1000);

    return () => {
      if (elapsedTimerRef.current) {
        clearInterval(elapsedTimerRef.current);
        elapsedTimerRef.current = null;
      }
    };
  }, [loading]);

  const reasons = Array.isArray(result?.reasons) ? result.reasons : [];

  const parsedScreenshotUrl = (() => {
    if (result?.screenshotUrl) return result.screenshotUrl;
    const screenshotReason = reasons.find((item) => item.startsWith("Screenshot:"));
    if (!screenshotReason) return "";
    return screenshotReason.replace("Screenshot:", "").trim();
  })();

  const visibleReasons = reasons.filter((item) => !item.startsWith("Screenshot:"));

  const finalScore = Math.max(0, Math.min(result?.finalScore ?? result?.riskScore ?? 0, 100));
  const status = result?.verdict || result?.status || "";
  const message = result?.message || "";
  const scannedUrl = result?.scannedUrl || "";
  const breakdown = {
    malware: Number.isFinite(result?.malwareScore) ? result.malwareScore : (result?.breakdown?.malware || 0),
    phishing: Number.isFinite(result?.phishingScore) ? result.phishingScore : (result?.breakdown?.phishing || 0),
    spam: Number.isFinite(result?.spamScore) ? result.spamScore : (result?.breakdown?.spam || 0),
    piracy: Number.isFinite(result?.piracyScore) ? result.piracyScore : (result?.breakdown?.piracy || 0),
    redirect: Number.isFinite(result?.redirectScore) ? result.redirectScore : (result?.breakdown?.redirect || 0),
    domain: Number.isFinite(result?.domainScore) ? result.domainScore : (result?.breakdown?.domain || 0),
  };
  const categoryLabels = result?.categoryLabels || {};
  const checksPerformed = Array.isArray(result?.checksPerformed) ? result.checksPerformed : [];
  const redirectChain = Array.isArray(result?.redirectChain) ? result.redirectChain : [];
  const finalUrl = result?.finalUrl || "";
  const resultUrl = result?.resultUrl || (result?.urlscanScanId ? `https://urlscan.io/result/${result.urlscanScanId}/` : "");
  const screenshotPending = Boolean(result?.urlscanScanId && !parsedScreenshotUrl);
  const contactedDomains = (() => {
    if (Array.isArray(result?.domains) && result.domains.length > 0) {
      return result.domains;
    }
    return Array.isArray(result?.contactedDomains) ? result.contactedDomains : [];
  })();
  const totalRequests = Number.isFinite(result?.totalRequests) ? result.totalRequests : 0;

  const categoryItems = useMemo(
    () => [
      { key: "malware", title: "Malware" },
      { key: "phishing", title: "Phishing" },
      { key: "piracy", title: "Piracy" },
      { key: "spam", title: "Spam" },
      { key: "redirect", title: "Redirect Risk" },
      { key: "domain", title: "Domain Risk" },
    ],
    []
  );

  const partialAnalysis = (() => {
    const merged = [...reasons, ...checksPerformed].join(" ").toLowerCase();
    return merged.includes("partial") || merged.includes("unavailable") || merged.includes("fallback");
  })();

  const isTerminalStatus = (value) =>
    ["SAFE", "MALICIOUS", "SUSPICIOUS", "UNKNOWN"].includes((value || "").toUpperCase());

  const pollScanStatus = (currentScanId) => {
    let attempts = 0;

    const pollOnce = async () => {
      attempts += 1;
      setPollAttempt(attempts);

      try {
        const response = await scannerApi.getScanStatus(currentScanId);
        const data = response?.data || null;

        if (!response || response.status < 200 || response.status >= 300) {
          clearPollingTimers();
          setLoading(false);
          setElapsedSeconds(0);
          setError(data?.message || "Failed to fetch scan status.");
          return;
        }

        const nextStatus = (data?.status || "").toUpperCase();
        if (isTerminalStatus(nextStatus)) {
          clearPollingTimers();
          setLoading(false);
          setElapsedSeconds(0);
          setPollAttempt(0);
          setResult(data?.result || { status: nextStatus, message: data?.errorMessage || "Scan completed" });
          if (!data?.result && data?.errorMessage) {
            setError(data?.errorMessage);
          }
          return;
        }

        if (attempts >= MAX_POLL_ATTEMPTS) {
          clearPollingTimers();
          try {
            const finalResponse = await scannerApi.getScanStatus(currentScanId);
            const finalData = finalResponse?.data || null;
            const finalStatus = (finalData?.status || "").toUpperCase();
            if (isTerminalStatus(finalStatus)) {
              setLoading(false);
              setElapsedSeconds(0);
              setPollAttempt(0);
              setResult(finalData?.result || { status: finalStatus, message: finalData?.errorMessage || "Scan completed" });
              if (!finalData?.result && finalData?.errorMessage) {
                setError(finalData.errorMessage);
              }
              return;
            }
          } catch {
            // Ignore and surface timeout message below.
          }

          setLoading(false);
          setElapsedSeconds(0);
          setError("Scan timed out after maximum polling attempts.");
        }
      } catch (error) {
        clearPollingTimers();
        setLoading(false);
        setElapsedSeconds(0);
        setError(error?.response?.data?.message || "Unable to reach scanner status endpoint.");
      }
    };

    pollOnce();
    pollIntervalRef.current = setInterval(pollOnce, POLL_INTERVAL_MS);
  };

  const handleScan = async (event) => {
    event?.preventDefault();

    const normalizedInput = normalizeScannerInput(url);
    if (!normalizedInput) {
      setError("Please enter a URL.");
      return;
    }

    clearPollingTimers();
    setLoading(true);
    setError("");
    setScanId("");
    setPollAttempt(0);
    setElapsedSeconds(0);
    setCopied(false);
    setCopiedFinal(false);

    try {
      const response = await scannerApi.submitScan(normalizedInput);
      const data = response?.data || null;

      const newScanId = data?.scanId || "";
      if (!newScanId) {
        setLoading(false);
        setElapsedSeconds(0);
        setError("Scan created without scanId. Please try again.");
        return;
      }

      setScanId(newScanId);
      pollScanStatus(newScanId);
    } catch (error) {
      setLoading(false);
      setElapsedSeconds(0);
      setError(error?.response?.data?.message || "Unable to reach scanner service. Please check backend and try again.");
    }
  };

  const normalizeScannerInput = (inputValue) => {
    const trimmed = (inputValue || "").trim();
    if (!trimmed) {
      return "";
    }

    // Keep legacy /s links scannable by forcing backend HTTP redirect endpoint.
    const legacyPrefix = `${import.meta.env.VITE_REACT_FRONT_END_URL || "http://localhost:5173"}/s/`;
    if (trimmed.startsWith(legacyPrefix)) {
      return trimmed.replace("/s/", "/r/").replace(import.meta.env.VITE_REACT_FRONT_END_URL || "http://localhost:5173", backendBaseUrl);
    }
    return trimmed;
  };

  const cancelScan = () => {
    clearPollingTimers();
    setLoading(false);
    setScanId("");
    setPollAttempt(0);
    setElapsedSeconds(0);
    setError("Scan cancelled by user. Previous result is still shown.");
  };

  const copyScannedUrl = async () => {
    if (!scannedUrl) return;

    try {
      await navigator.clipboard.writeText(scannedUrl);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      setError("Unable to copy URL. Please copy manually.");
    }
  };

  const hasResult = Boolean(status || message || scannedUrl || reasons.length > 0 || Object.keys(breakdown).length > 0);
  const pollProgressPercent = Math.min((pollAttempt / MAX_POLL_ATTEMPTS) * 100, 100);

  const copyFinalUrl = async () => {
    if (!finalUrl) return;

    try {
      await navigator.clipboard.writeText(finalUrl);
      setCopiedFinal(true);
      setTimeout(() => setCopiedFinal(false), 1500);
    } catch {
      setError("Unable to copy final URL. Please copy manually.");
    }
  };

  return (
    <Motion.div
      initial={{ opacity: 0, y: 24 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
      className="min-h-[calc(100vh-64px)] lg:px-14 sm:px-8 px-4 py-8"
    >
      <div className="max-w-6xl mx-auto glass-card sm:p-8 p-4">
        <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
          <div className="text-xs sm:text-sm text-slate-300">
            <Link to="/" className="hover:text-white">Home</Link>
            <span className="mx-2 text-slate-500">/</span>
            <span className="text-slate-200">Scanner</span>
          </div>
          <div className="flex gap-2 w-full sm:w-auto">
            <Motion.button
              type="button"
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={() => navigate("/dashboard")}
              className="flex-1 sm:flex-none text-sm border border-white/25 text-slate-100 rounded-xl px-3 py-2 hover:bg-white/10 transition-colors duration-200"
            >
              Back to Dashboard
            </Motion.button>
            <Motion.button
              type="button"
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={() => navigate("/")}
              className="flex-1 sm:flex-none text-sm border border-white/25 text-slate-100 rounded-xl px-3 py-2 hover:bg-white/10 transition-colors duration-200"
            >
              Home
            </Motion.button>
          </div>
        </div>

        <h1 className="text-2xl font-serif font-bold text-white">URL Scanner</h1>
        <p className="text-slate-200 mt-1 text-sm">
          Security dashboard for URL risk analysis and behavior insights.
        </p>

        <form onSubmit={handleScan} className="mt-6 flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <label htmlFor="url" className="font-semibold text-md text-slate-100">
              Enter URL
            </label>
            <input
              id="url"
              type="url"
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="http://localhost:9000/r/abc123 or https://example.com"
              className="px-3 py-2.5 border border-white/20 outline-none bg-slate-900/40 text-slate-100 rounded-xl placeholder:text-slate-400"
            />
          </div>

          <div className="flex sm:flex-row flex-col items-center gap-3">
            <Motion.button
              type="submit"
              disabled={loading}
              whileHover={{ scale: loading ? 1 : 1.05 }}
              whileTap={{ scale: loading ? 1 : 0.95 }}
              className="btn-gradient sm:w-32 w-full disabled:opacity-70 disabled:cursor-not-allowed"
            >
              {loading ? "Scanning..." : "Scan"}
            </Motion.button>

            <Motion.button
              type="button"
              onClick={clearAll}
              disabled={loading || (!hasResult && !error && !url)}
              whileHover={{ scale: loading ? 1 : 1.05 }}
              whileTap={{ scale: loading ? 1 : 0.95 }}
              className="border border-white/25 text-slate-100 font-semibold sm:w-32 w-full py-2.5 rounded-xl hover:bg-white/10 transition-colors duration-200 disabled:opacity-50"
            >
              Clear
            </Motion.button>

            {loading && (
              <Motion.button
                type="button"
                onClick={cancelScan}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="border border-rose-300/40 text-rose-100 font-semibold sm:w-32 w-full py-2.5 rounded-xl hover:bg-rose-500/15 transition-colors duration-200"
              >
                Cancel
              </Motion.button>
            )}
          </div>
        </form>

        {loading && (
          <div className="mt-6 border border-blue-300/30 rounded-2xl p-4 bg-blue-500/10">
            <div className="flex items-center gap-3">
              <span className="inline-block h-5 w-5 rounded-full border-2 border-blue-300 border-t-blue-700 animate-spin" />
              <p className="text-blue-200 text-sm font-semibold">
                Scanning in progress...
                {scanId ? ` (Scan: ${scanId})` : ""}
              </p>
            </div>
            {scanId && (
              <>
                <p className="text-blue-200 text-xs mt-1">
                  Poll attempt: {Math.min(pollAttempt, MAX_POLL_ATTEMPTS)}/{MAX_POLL_ATTEMPTS}
                </p>
                <p className="text-blue-200 text-xs mt-1">Elapsed: {elapsedSeconds}s</p>
                <div className="w-full h-2 bg-blue-900/50 rounded-full mt-2 overflow-hidden">
                  <div
                    className="h-2 bg-blue-400 transition-all duration-300"
                    style={{ width: `${pollProgressPercent}%` }}
                  />
                </div>
              </>
            )}
          </div>
        )}

        {error && (
          <div className="mt-6 border border-rose-300/30 rounded-2xl p-4 bg-rose-500/10">
            <p className="text-rose-100 text-sm font-semibold">{error}</p>
            {!loading && (
              <Motion.button
                type="button"
                onClick={handleScan}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                className="mt-3 border border-rose-300/40 text-rose-100 text-sm font-semibold px-3 py-1 rounded-md hover:bg-rose-500/15 transition-colors duration-100"
              >
                Try Again
              </Motion.button>
            )}
          </div>
        )}

        {hasResult && partialAnalysis && (
          <div className="mt-6 border border-amber-300/30 rounded-2xl p-4 bg-amber-500/10">
            <p className="text-amber-100 text-sm font-semibold">
              Partial analysis - some services unavailable.
            </p>
          </div>
        )}

        {hasResult && (
          <Motion.div
            initial={{ opacity: 0, y: 16 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.45 }}
            className="mt-8 space-y-4"
          >
            <ResultHeader
              status={status}
              message={message}
              scannedUrl={scannedUrl}
              finalScore={finalScore}
              onCopyScannedUrl={copyScannedUrl}
              copied={copied}
            />

            <section className="glass-card p-6">
              <h3 className="text-base font-semibold text-white">Category Breakdown</h3>
              <div className="mt-4 grid grid-cols-2 lg:grid-cols-3 gap-3">
                {categoryItems.map((item) => (
                  <CategoryCard
                    key={item.key}
                    title={item.title}
                    score={Number.isFinite(breakdown[item.key]) ? breakdown[item.key] : 0}
                    label={categoryLabels[item.key]}
                  />
                ))}
              </div>
            </section>

            <BehaviorPanel
              finalUrl={finalUrl}
              redirectChain={redirectChain}
              totalRequests={totalRequests}
              domains={contactedDomains}
              checksPerformed={checksPerformed}
              onCopyFinalUrl={copyFinalUrl}
              copiedFinal={copiedFinal}
            />

            <ScreenshotPanel
              key={result?.urlscanScanId || parsedScreenshotUrl || resultUrl || "screenshot-panel"}
              screenshotUrl={parsedScreenshotUrl}
              resultUrl={resultUrl}
              isProcessing={loading || screenshotPending}
            />

            <ReasonsList reasons={visibleReasons} />
          </Motion.div>
        )}
      </div>
    </Motion.div>
  );
};

export default UrlScannerPage;

