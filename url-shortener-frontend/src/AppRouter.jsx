import React, { Suspense, lazy } from "react";
import { Route, Routes, useLocation } from "react-router-dom";
import Navbar from "./components/NavBar";
import ShortenUrlPage from "./components/ShortenUrlPage";
import { Toaster } from "react-hot-toast";
import Footer from "./components/Footer";
import LandingPage from "./components/LandingPage";
import AboutPage from "./components/AboutPage";
import RegisterPage from "./components/RegisterPage";
import LoginPage from "./components/LoginPage";
import DashboardLayout from "./components/Dashboard/DashboardLayout";
import PrivateRoute from "./PrivateRoute";
import ErrorPage from "./components/ErrorPage";
import UrlScannerPage from "./components/UrlScannerPage";
import RegionAnalytics from "./components/RegionAnalytics";

const AmbientBackground3D = lazy(() => import("./components/AmbientBackground3D"));

// <PrivateRoute publicPage={true}>
//      <RegisterPage />
// </PrivateRoute>

const AppRouter = () => {
  const location = useLocation();
  const hideHeaderFooter = location.pathname.startsWith("/s");

    return (
        <div className="app-gradient-bg relative min-h-screen">
        <div className="noise-overlay" />
        <Suspense fallback={null}>
          <AmbientBackground3D />
        </Suspense>
        <div className="relative z-10">
        {!hideHeaderFooter && <Navbar /> }
        <Toaster position='bottom-center'/>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/about" element={<AboutPage />} />
          <Route path="/scanner" element={<UrlScannerPage />} />
          <Route path="/s/:url" element={<ShortenUrlPage />} />

          <Route path="/register" element={<PrivateRoute publicPage={true}><RegisterPage /></PrivateRoute>} />
          <Route path="/login" element={<PrivateRoute publicPage={true}><LoginPage /></PrivateRoute>} />
          
          <Route path="/dashboard" element={ <PrivateRoute publicPage={false}><DashboardLayout /></PrivateRoute>} />
          <Route path="/region-analytics" element={ <PrivateRoute publicPage={false}><RegionAnalytics /></PrivateRoute>} />
          <Route path="/error" element={ <ErrorPage />} />
          <Route path="*" element={ <ErrorPage message="We can't seem to find the page you're looking for"/>} />
        </Routes>
        {!hideHeaderFooter && <Footer />}
        </div>
      </div>
    );
}


export default AppRouter;

export const SubDomainRouter = () => {
    return (
        <Routes>
          <Route path="/:url" element={<ShortenUrlPage />} />
        </Routes>
    )
}