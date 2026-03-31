import React, { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import toast from "react-hot-toast";
import TextField from "./TextField";
import api from "../api/api";

const UrlScannerPage = () => {
  const [loading, setLoading] = useState(false);
  const [scanResult, setScanResult] = useState(null);
  const [scanError, setScanError] = useState(null);
  const resultRef = useRef(null);
  const errorRef = useRef(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    defaultValues: {
      url: "",
    },
    mode: "onTouched",
  });

  const onScanHandler = async (formData) => {
    setLoading(true);
    setScanError(null);
    setScanResult(null);
    try {
      const { data } = await api.post("/api/scan", {
        url: formData.url,
      });
      setScanResult(data);
      reset({ url: "" });
    } catch (error) {
      const backendData = error?.response?.data;
      const backendMessage = backendData?.message;
      setScanError({
        message: backendMessage || "Scan failed. Please try again.",
        reasons: backendData?.reasons || [],
      });
      toast.error(backendMessage || "Scan failed. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const clearScanOutput = () => {
    setScanResult(null);
    setScanError(null);
  };

  const getStatusLabel = (status) => {
    if (status === "SAFE") return "Safe";
    if (status === "SUSPICIOUS") return "Suspicious";
    if (status === "UNSAFE") return "Dangerous";
    return status;
  };

  const getStatusStyle = (status) => {
    if (status === "SAFE") return "text-green-700";
    if (status === "SUSPICIOUS") return "text-amber-700";
    if (status === "UNSAFE") return "text-red-700";
    return "text-slate-800";
  };

  const getScoreBarStyle = (status) => {
    if (status === "SAFE") return "bg-green-600";
    if (status === "SUSPICIOUS") return "bg-amber-500";
    if (status === "UNSAFE") return "bg-red-600";
    return "bg-slate-500";
  };

  const scoreValue = scanResult ? Math.max(0, Math.min(scanResult.riskScore || 0, 100)) : 0;

  useEffect(() => {
    if (scanResult && resultRef.current) {
      resultRef.current.scrollIntoView({ behavior: "smooth", block: "start" });
      return;
    }

    if (scanError && errorRef.current) {
      errorRef.current.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  }, [scanResult, scanError]);

  return (
    <div className="min-h-[calc(100vh-64px)] lg:px-14 sm:px-8 px-4 py-8">
      <div className="max-w-2xl mx-auto shadow-custom rounded-md sm:p-8 p-4 bg-white">
        <h1 className="text-2xl font-serif font-bold text-btnColor">URL Scanner</h1>
        <p className="text-slate-600 mt-1 text-sm">Check whether a URL is safe, suspicious, or dangerous.</p>

        <form onSubmit={handleSubmit(onScanHandler)} className="mt-6 flex flex-col gap-4">
          <TextField
            label="Enter URL"
            id="url"
            type="url"
            required
            message="URL is required"
            placeholder="https://example.com"
            register={register}
            errors={errors}
            validate={(value) => {
              try {
                new URL(value);
                return true;
              } catch (error) {
                return "Please enter a valid URL";
              }
            }}
          />
          <p className="text-slate-500 text-xs">Use a full URL like `https://example.com`.</p>

          <div className="flex sm:flex-row flex-col items-center gap-3">
            <button
              type="submit"
              disabled={loading}
              className="bg-customRed font-semibold text-white bg-custom-gradient sm:w-32 w-full py-2 rounded-md hover:text-slate-300 transition-colors duration-100"
            >
              {loading ? "Scanning..." : "Scan"}
            </button>

            <button
              type="button"
              onClick={clearScanOutput}
              disabled={loading || (!scanResult && !scanError)}
              className="border border-slate-500 text-slate-700 font-semibold sm:w-32 w-full py-2 rounded-md hover:bg-slate-100 transition-colors duration-100 disabled:opacity-50"
            >
              Clear Result
            </button>
          </div>
        </form>

        {scanError && (
          <div
            ref={errorRef}
            className="mt-8 border border-red-200 rounded-md p-4 bg-red-50"
            role="status"
            aria-live="polite"
          >
            <h2 className="font-bold text-lg text-red-700">Scan Error</h2>
            <p className="text-red-700 mt-1 text-sm">{scanError.message}</p>
            {scanError.reasons.length > 0 && (
              <ul className="list-disc ml-5 mt-2 text-red-700 text-sm space-y-1">
                {scanError.reasons.map((reason, index) => (
                  <li key={`${reason}-${index}`}>{reason}</li>
                ))}
              </ul>
            )}
          </div>
        )}

        {scanResult && (
          <div
            ref={resultRef}
            className="mt-8 border border-slate-300 rounded-md p-4 bg-slate-50"
            role="status"
            aria-live="polite"
          >
            <h2 className="font-bold text-lg text-slate-900">Scan Result</h2>
            <p className={`mt-2 font-semibold ${getStatusStyle(scanResult.status)}`}>
              Status: {getStatusLabel(scanResult.status)}
            </p>
            <p className="text-slate-700 mt-1 text-sm">{scanResult.message}</p>
            <p className="text-slate-700 mt-1 text-sm break-all">Scanned URL: {scanResult.scannedUrl}</p>

            <div className="mt-3">
              <div className="flex justify-between text-slate-700 text-sm font-semibold">
                <span>Risk Score</span>
                <span>{scoreValue}/100</span>
              </div>
              <div className="w-full h-2 bg-slate-200 rounded-full mt-2 overflow-hidden">
                <div
                  className={`h-2 ${getScoreBarStyle(scanResult.status)}`}
                  style={{ width: `${scoreValue}%` }}
                />
              </div>
            </div>

            <div className="mt-4">
              <h3 className="font-semibold text-slate-800">Reasons:</h3>
              {scanResult.reasons && scanResult.reasons.length > 0 ? (
                <ul className="list-disc ml-5 mt-1 text-slate-700 text-sm space-y-1">
                  {scanResult.reasons.map((reason, index) => (
                    <li key={`${reason}-${index}`}>{reason}</li>
                  ))}
                </ul>
              ) : (
                <p className="text-slate-700 text-sm mt-1">No reasons provided by scanner.</p>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default UrlScannerPage;

