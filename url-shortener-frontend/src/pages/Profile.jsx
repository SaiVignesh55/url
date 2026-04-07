import { useEffect, useMemo, useState } from "react";
import { Navigate } from "react-router-dom";
const Profile = () => {
  const backendUrl = (import.meta.env.VITE_BACKEND_URL || "").trim();
  const token = useMemo(() => {
    const rawToken = localStorage.getItem("JWT_TOKEN");
    if (!rawToken) {
      return null;
    }
    try {
      return JSON.parse(rawToken);
    } catch {
      return rawToken;
    }
  }, []);
  const [profile, setProfile] = useState({ name: "", email: "" });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    if (!token) {
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    const fetchProfile = async () => {
      try {
        setLoading(true);
        setError("");
        const response = await fetch(`${backendUrl}/api/user/profile`, {
          method: "GET",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${token}`,
          },
          signal: controller.signal,
        });
        if (!response.ok) {
          throw new Error("Failed to load profile data");
        }
        const data = await response.json();
        setProfile({
          name: data?.name || "-",
          email: data?.email || "-",
        });
      } catch (err) {
        if (err.name !== "AbortError") {
          setError(err.message || "Unable to fetch profile");
        }
      } finally {
        setLoading(false);
      }
    };
    fetchProfile();
    return () => controller.abort();
  }, [backendUrl, token]);
  if (!token) {
    return <Navigate to="/login" replace />;
  }
  return (
    <div className="min-h-[calc(100vh-64px)] flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-xl glass-card p-6 sm:p-8 ring-1 ring-blue-300/30">
        <h1 className="text-2xl sm:text-3xl font-bold text-white text-center">Profile</h1>
        {loading && <p className="text-center text-slate-300 mt-6">Loading...</p>}
        {!loading && error && (
          <p className="text-center text-red-300 mt-6 font-medium">{error}</p>
        )}
        {!loading && !error && (
          <div className="mt-6 space-y-4">
            <div className="bg-slate-900/40 border border-white/10 rounded-xl p-4">
              <p className="text-slate-400 text-sm">Name</p>
              <p className="text-white text-lg font-semibold mt-1">{profile.name}</p>
            </div>
            <div className="bg-slate-900/40 border border-white/10 rounded-xl p-4">
              <p className="text-slate-400 text-sm">Email</p>
              <p className="text-white text-lg font-semibold mt-1 break-all">{profile.email}</p>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
export default Profile;