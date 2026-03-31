import React, { useEffect, useMemo, useRef, useState } from "react";

const ASYNC_SCAN_URL = "http://localhost:9000/api/scan/async";
const SYNC_SCAN_URL = "http://localhost:9000/api/scan";
const POLL_INTERVAL_MS = 2000;
const POLL_TIMEOUT_MS = 20000;
const MAX_POLL_ATTEMPTS = POLL_TIMEOUT_MS / POLL_INTERVAL_MS;

const UrlScannerPage = () => {
  const [url, setUrl] = useState("");
  const [jobId, setJobId] = useState("");
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [pollAttempt, setPollAttempt] = useState(0);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const [fallbackMode, setFallbackMode] = useState(false);
  const [copied, setCopied] = useState(false);
  const [copiedFinal, setCopiedFinal] = useState(false);
  const backendBaseUrl = import.meta.env.VITE_BACKEND_URL || "http://localhost:9000";

  const pollIntervalRef = useRef(null);
  const pollTimeoutRef = useRef(null);
  const elapsedTimerRef = useRef(null);

  const clearPollingTimers = () => {
    if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
      pollIntervalRef.current = null;
    }
    if (pollTimeoutRef.current) {
      clearTimeout(pollTimeoutRef.current);
      pollTimeoutRef.current = null;
    }
    if (elapsedTimerRef.current) {
      clearInterval(elapsedTimerRef.current);
      elapsedTimerRef.current = null;
    }
  };

  const clearAll = () => {
    clearPollingTimers();
    setUrl("");
    setJobId("");
    setResult(null);
    setError("");
    setLoading(false);
    setPollAttempt(0);
    setElapsedSeconds(0);
    setFallbackMode(false);
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

  const getStatusStyle = (currentStatus) => {
    if (currentStatus === "SAFE") return "text-green-700 bg-green-50 border-green-200";
    if (currentStatus?.startsWith("SUSPICIOUS")) return "text-amber-700 bg-amber-50 border-amber-200";
    if (currentStatus === "DANGEROUS") return "text-red-700 bg-red-50 border-red-200";
    if (currentStatus === "INVALID_REQUEST") return "text-slate-700 bg-slate-100 border-slate-300";
    return "text-slate-700 bg-slate-100 border-slate-300";
  };

  const getScoreBarStyle = (currentStatus) => {
    if (currentStatus === "SAFE") return "bg-green-600";
    if (currentStatus?.startsWith("SUSPICIOUS")) return "bg-amber-500";
    if (currentStatus === "DANGEROUS") return "bg-red-600";
    return "bg-slate-500";
  };

  const reasons = useMemo(
    () => (Array.isArray(result?.reasons) ? result.reasons : []),
    [result?.reasons]
  );

  const parsedScreenshotUrl = useMemo(() => {
    if (result?.screenshotUrl) return result.screenshotUrl;
    const screenshotReason = reasons.find((item) => item.startsWith("Screenshot:"));
    if (!screenshotReason) return "";
    return screenshotReason.replace("Screenshot:", "").trim();
  }, [reasons, result?.screenshotUrl]);

  const visibleReasons = useMemo(
    () => reasons.filter((item) => !item.startsWith("Screenshot:")),
    [reasons]
  );

  const finalScore = Math.max(
    0,
    Math.min(result?.finalScore ?? result?.riskScore ?? 0, 100)
  );
  const status = result?.status || "";
  const message = result?.message || "";
  const scannedUrl = result?.scannedUrl || "";
  const breakdown = result?.breakdown || {};
  const categoryLabels = result?.categoryLabels || {};
  const checksPerformed = Array.isArray(result?.checksPerformed) ? result.checksPerformed : [];
  const redirectChain = Array.isArray(result?.redirectChain) ? result.redirectChain : [];
  const finalUrl = result?.finalUrl || "";

  const runSyncScan = async (urlToScan) => {
    clearPollingTimers();
    setJobId("");
    setPollAttempt(0);
    setFallbackMode(true);
    setCopied(false);
    setCopiedFinal(false);

    try {
      const response = await fetch(SYNC_SCAN_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: urlToScan }),
      });

      let data = null;
      try {
        data = await response.json();
      } catch {
        data = null;
      }

      if (!response.ok) {
        setError(data?.message || "Scan failed. Please try again.");
        setResult(data || null);
        return;
      }

      setResult(data || null);
      if (data?.status === "INVALID_REQUEST") {
        setError(data?.message || "Invalid request.");
      }
    } catch {
      setError("Unable to reach scanner service. Please check backend and try again.");
    } finally {
      setLoading(false);
      setElapsedSeconds(0);
    }
  };

  const pollScanStatus = (currentJobId, urlToScan) => {
    let attempts = 0;

    const pollOnce = async () => {
      attempts += 1;
      setPollAttempt(attempts);

      try {
        const response = await fetch(`${ASYNC_SCAN_URL}/${currentJobId}`);

        let data = null;
        try {
          data = await response.json();
        } catch {
          data = null;
        }

        if (!response.ok) {
          await runSyncScan(urlToScan);
          return;
        }

        if (data?.status === "COMPLETED") {
          clearPollingTimers();
          setLoading(false);
          setPollAttempt(0);
          setElapsedSeconds(0);
          setResult(data?.result || null);
          setFallbackMode(false);

          if (!data?.result) {
            setError("Scan completed but no result received.");
          } else if (data.result.status === "INVALID_REQUEST") {
            setError(data.result.message || "Invalid request.");
          }
          return;
        }

        if (data?.status === "NOT_SUPPORTED") {
          await runSyncScan(urlToScan);
        }
      } catch {
        await runSyncScan(urlToScan);
      }
    };

    pollOnce();

    pollIntervalRef.current = setInterval(() => {
      pollOnce();
    }, POLL_INTERVAL_MS);

    pollTimeoutRef.current = setTimeout(() => {
      clearPollingTimers();
      setLoading(false);
      setPollAttempt(0);
      setElapsedSeconds(0);
      setError("Scan taking too long");
    }, POLL_TIMEOUT_MS);
  };

  const handleScan = async (event) => {
    event.preventDefault();

    const normalizedInput = normalizeScannerInput(url);
    if (!normalizedInput) {
      setError("Please enter a URL.");
      return;
    }

    clearPollingTimers();
    setLoading(true);
    setError("");
    setJobId("");
    setPollAttempt(0);
    setElapsedSeconds(0);
    setFallbackMode(false);
    setCopied(false);
    setCopiedFinal(false);

    try {
      const response = await fetch(ASYNC_SCAN_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ url: normalizedInput }),
      });

      let data = null;
      try {
        data = await response.json();
      } catch {
        data = null;
      }

      if (!response.ok) {
        await runSyncScan(normalizedInput);
        return;
      }

      const newJobId = data?.jobId || "";
      if (!newJobId) {
        await runSyncScan(normalizedInput);
        return;
      }

      setJobId(newJobId);
      pollScanStatus(newJobId, normalizedInput);
    } catch {
      await runSyncScan(normalizedInput);
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
    setJobId("");
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

  const hasResult = Boolean(status || message || scannedUrl || reasons.length > 0);
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
    <div className="min-h-[calc(100vh-64px)] lg:px-14 sm:px-8 px-4 py-8">
      <div className="max-w-2xl mx-auto shadow-custom rounded-md sm:p-8 p-4 bg-white">
        <h1 className="text-2xl font-serif font-bold text-btnColor">URL Scanner</h1>
        <p className="text-slate-600 mt-1 text-sm">
          Check whether a URL is safe, suspicious, or dangerous.
        </p>

        <form onSubmit={handleScan} className="mt-6 flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <label htmlFor="url" className="font-semibold text-md">
              Enter URL
            </label>
            <input
              id="url"
              type="url"
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="http://localhost:9000/r/abc123 or https://example.com"
              className="px-2 py-2 border border-slate-600 outline-none bg-transparent text-slate-700 rounded-md"
            />
          </div>

          <div className="flex sm:flex-row flex-col items-center gap-3">
            <button
              type="submit"
              disabled={loading}
              className="bg-customRed font-semibold text-white bg-custom-gradient sm:w-32 w-full py-2 rounded-md hover:text-slate-300 transition-colors duration-100 disabled:opacity-70"
            >
              {loading ? "Scanning..." : "Scan"}
            </button>

            <button
              type="button"
              onClick={clearAll}
              disabled={loading || (!hasResult && !error && !url)}
              className="border border-slate-500 text-slate-700 font-semibold sm:w-32 w-full py-2 rounded-md hover:bg-slate-100 transition-colors duration-100 disabled:opacity-50"
            >
              Clear
            </button>

            {loading && (
              <button
                type="button"
                onClick={cancelScan}
                className="border border-red-500 text-red-600 font-semibold sm:w-32 w-full py-2 rounded-md hover:bg-red-50 transition-colors duration-100"
              >
                Cancel
              </button>
            )}
          </div>
        </form>

        {loading && (
          <div className="mt-6 border border-blue-200 rounded-md p-4 bg-blue-50">
            <p className="text-blue-700 text-sm font-semibold">
              Scanning in progress...
              {jobId ? ` (Job: ${jobId})` : ""}
            </p>
            {jobId && (
              <>
                <p className="text-blue-700 text-xs mt-1">
                  Poll attempt: {Math.min(pollAttempt, MAX_POLL_ATTEMPTS)}/{MAX_POLL_ATTEMPTS}
                </p>
                <p className="text-blue-700 text-xs mt-1">Elapsed: {elapsedSeconds}s</p>
                <div className="w-full h-2 bg-blue-100 rounded-full mt-2 overflow-hidden">
                  <div
                    className="h-2 bg-blue-500 transition-all duration-300"
                    style={{ width: `${pollProgressPercent}%` }}
                  />
                </div>
              </>
            )}
            {fallbackMode && (
              <p className="text-amber-700 text-xs mt-2 font-semibold">
                Fallback mode active: async scan unavailable, running direct scan.
              </p>
            )}
          </div>
        )}

        {error && (
          <div className="mt-6 border border-red-200 rounded-md p-4 bg-red-50">
            <p className="text-red-700 text-sm font-semibold">{error}</p>
            {!loading && (
              <button
                type="button"
                onClick={handleScan}
                className="mt-3 border border-red-400 text-red-700 text-sm font-semibold px-3 py-1 rounded-md hover:bg-red-100 transition-colors duration-100"
              >
                Try Again
              </button>
            )}
          </div>
        )}

        {hasResult && (
          <div className="mt-8 border border-slate-300 rounded-md p-4 bg-slate-50">
            <h2 className="font-bold text-lg text-slate-900">Scan Result</h2>

            <div className={`mt-3 border rounded-md px-3 py-2 inline-block text-sm font-semibold ${getStatusStyle(status)}`}>
              STATUS: {status || "N/A"}
            </div>

            <p className="text-slate-700 mt-3 text-sm">
              <span className="font-semibold">Message:</span> {message || "No message"}
            </p>

            <p className="text-slate-700 mt-1 text-sm break-all">
              <span className="font-semibold">Scanned URL:</span> {scannedUrl || "-"}
            </p>

            {scannedUrl && (
              <div className="mt-2">
                <button
                  type="button"
                  onClick={copyScannedUrl}
                  className="border border-slate-400 text-slate-700 text-xs font-semibold px-3 py-1 rounded-md hover:bg-slate-100 transition-colors duration-100"
                >
                  {copied ? "Copied" : "Copy Scanned URL"}
                </button>
              </div>
            )}

            <div className="mt-4">
              <div className="flex justify-between text-slate-700 text-sm font-semibold">
                <span>Risk Score</span>
                <span>{finalScore}/100</span>
              </div>
              <div className="w-full h-2 bg-slate-200 rounded-full mt-2 overflow-hidden">
                <div
                  className={`h-2 ${getScoreBarStyle(status)} transition-all duration-500`}
                  style={{ width: `${finalScore}%` }}
                />
              </div>
            </div>

            {Object.keys(breakdown).length > 0 && (
              <div className="mt-4">
                <h3 className="font-semibold text-slate-800">Category Scores:</h3>
                <div className="mt-2 grid sm:grid-cols-2 grid-cols-1 gap-2">
                  {Object.entries(breakdown).map(([key, value]) => (
                    <div key={key} className="border border-slate-300 rounded-md p-2 bg-white">
                      <p className="text-xs text-slate-500">{key}</p>
                      <p className="text-sm font-semibold text-slate-800">
                        {value} / {categoryLabels[key] || "N/A"}
                      </p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {checksPerformed.length > 0 && (
              <div className="mt-4">
                <h3 className="font-semibold text-slate-800">Checks Performed:</h3>
                <ul className="list-disc ml-5 mt-1 text-slate-700 text-sm space-y-1">
                  {checksPerformed.map((item, index) => (
                    <li key={`${item}-${index}`}>{item}</li>
                  ))}
                </ul>
              </div>
            )}

            {(finalUrl || redirectChain.length > 0) && (
              <div className="mt-4">
                <h3 className="font-semibold text-slate-800">Redirect Analysis</h3>

                <p className="text-slate-700 mt-2 text-sm break-all">
                  <span className="font-semibold">Final URL:</span> {finalUrl || "-"}
                </p>

                {finalUrl && (
                  <div className="mt-2">
                    <button
                      type="button"
                      onClick={copyFinalUrl}
                      className="border border-slate-400 text-slate-700 text-xs font-semibold px-3 py-1 rounded-md hover:bg-slate-100 transition-colors duration-100"
                    >
                      {copiedFinal ? "Copied" : "Copy Final URL"}
                    </button>
                  </div>
                )}

                <div className="mt-3">
                  <p className="font-semibold text-slate-700 text-sm">Redirect Chain:</p>
                  {redirectChain.length > 0 ? (
                    <ul className="list-disc ml-5 mt-1 text-slate-700 text-sm space-y-1 break-all">
                      {redirectChain.map((item, index) => (
                        <li key={`${item}-${index}`}>{item}</li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-slate-700 text-sm mt-1">No redirects detected.</p>
                  )}
                </div>
              </div>
            )}

            <div className="mt-4">
              <h3 className="font-semibold text-slate-800">Reasons:</h3>
              {visibleReasons.length > 0 ? (
                <ul className="list-disc ml-5 mt-1 text-slate-700 text-sm space-y-1">
                  {visibleReasons.map((reason, index) => (
                    <li key={`${reason}-${index}`}>{reason}</li>
                  ))}
                </ul>
              ) : (
                <p className="text-slate-700 text-sm mt-1">No reasons provided by scanner.</p>
              )}
            </div>

            {parsedScreenshotUrl && (
              <div className="mt-6">
                <h3 className="font-semibold text-slate-800 mb-2">Screenshot</h3>
                <img
                  src={parsedScreenshotUrl}
                  alt="Scanned website preview"
                  className="w-full rounded-md border border-slate-300"
                  loading="lazy"
                />
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default UrlScannerPage;

