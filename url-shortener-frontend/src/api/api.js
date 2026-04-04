import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_BACKEND_URL,
});

export const scannerApi = {
  submitScan: (url) => api.post("/api/scan", { url }),
  getScanStatus: (scanId) => api.get(`/api/status/${scanId}`),
};

export const linksApi = {
  getMyLinks: (token) =>
    api.get("/api/urls/myurls", {
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        Authorization: `Bearer ${token}`,
      },
    }),
};

export const regionAnalyticsApi = {
  getRegionStatsByShortCode: (shortCode, token) =>
    api.get(`/api/region-stats/${shortCode}`, {
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        Authorization: `Bearer ${token}`,
      },
    }),
};

export default api;
