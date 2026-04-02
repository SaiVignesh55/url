import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_BACKEND_URL,
});

export const scannerApi = {
  submitScan: (url) => api.post("/api/scan", { url }),
  getScanStatus: (scanId) => api.get(`/api/status/${scanId}`),
};

export default api;
