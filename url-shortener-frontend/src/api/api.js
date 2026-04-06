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

export const cityAnalyticsApi = {
  getCityStatsByShortCode: async (shortCode, token) => {
    const config = {
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        Authorization: `Bearer ${token}`,
      },
    };

    try {
      return await api.get(`/api/city-stats/${shortCode}`, config);
    } catch (error) {
      if (error?.response?.status === 404) {
        return await api.get(`/api/country-stats/${shortCode}`, config);
      }
      throw error;
    }
  },
};

export default api;
