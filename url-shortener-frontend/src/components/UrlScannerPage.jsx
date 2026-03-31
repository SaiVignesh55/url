import React, { useState } from "react";
import { useForm } from "react-hook-form";
import toast from "react-hot-toast";
import TextField from "./TextField";
import api from "../api/api";

const UrlScannerPage = () => {
  const [loading, setLoading] = useState(false);
  const [scanResult, setScanResult] = useState(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({
    defaultValues: {
      url: "",
    },
    mode: "onTouched",
  });

  const onScanHandler = async (formData) => {
    setLoading(true);
    try {
      const { data } = await api.post("/api/scan", {
        url: formData.url,
      });
      setScanResult(data);
    } catch (error) {
      const backendMessage = error?.response?.data?.message;
      toast.error(backendMessage || "Scan failed. Please try again.");
      setScanResult(null);
    } finally {
      setLoading(false);
    }
  };

  const getStatusStyle = (status) => {
    if (status === "SAFE") {
      return "text-green-700";
    }
    if (status === "SUSPICIOUS") {
      return "text-amber-700";
    }
    if (status === "UNSAFE") {
      return "text-red-700";
    }
    return "text-slate-800";
  };

  return (
    <div className="min-h-[calc(100vh-64px)] lg:px-14 sm:px-8 px-4 py-8">
      <div className="max-w-2xl mx-auto shadow-custom rounded-md sm:p-8 p-4 bg-white">
        <h1 className="text-2xl font-serif font-bold text-btnColor">URL Scanner</h1>
        <p className="text-slate-600 mt-1 text-sm">
          Check whether a URL is safe, suspicious, or dangerous.
        </p>

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
          />

          <button
            type="submit"
            disabled={loading}
            className="bg-customRed font-semibold text-white bg-custom-gradient w-32 py-2 rounded-md hover:text-slate-300 transition-colors duration-100"
          >
            {loading ? "Scanning..." : "Scan"}
          </button>
        </form>

        {scanResult && (
          <div className="mt-8 border border-slate-300 rounded-md p-4 bg-slate-50">
            <h2 className="font-bold text-lg text-slate-900">Scan Result</h2>
            <p className={`mt-2 font-semibold ${getStatusStyle(scanResult.status)}`}>
              Status: {scanResult.status}
            </p>
            <p className="text-slate-700 mt-1">Risk Score: {scanResult.riskScore}</p>

            <div className="mt-3">
              <h3 className="font-semibold text-slate-800">Reasons:</h3>
              <ul className="list-disc ml-5 mt-1 text-slate-700 text-sm space-y-1">
                {(scanResult.reasons || []).map((reason, index) => (
                  <li key={`${reason}-${index}`}>{reason}</li>
                ))}
              </ul>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default UrlScannerPage;

