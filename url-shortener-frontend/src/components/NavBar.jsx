import React, { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { IoIosMenu } from "react-icons/io";
import { RxCross2 } from "react-icons/rx";
import { motion as Motion } from "framer-motion";
import { useStoreContext } from "../contextApi/ContextApi";

const Navbar = () => {
  const navigate = useNavigate();
  const { token, setToken } = useStoreContext();
  const path = useLocation().pathname;
  const [navbarOpen, setNavbarOpen] = useState(false);

  const onLogOutHandler = () => {
    setToken(null);
    localStorage.removeItem("JWT_TOKEN");
    navigate("/login");
  };

  const navItems = [
    { path: "/", label: "Home" },
    { path: "/about", label: "About" },
    { path: "/scanner", label: "URL Scanner" },
    ...(token ? [{ path: "/dashboard", label: "Dashboard" }] : []),
  ];

  return (
    <header className="sticky top-0 z-50 border-b border-white/10 bg-slate-900/65 backdrop-blur-xl">
      <div className="lg:px-14 sm:px-8 px-4 h-16 w-full flex justify-between items-center">
        <Link to="/" className="relative z-20">
          <h1 className="font-bold text-3xl text-white italic">Linklytics</h1>
        </Link>

        <ul
          className={`sm:static absolute left-0 top-16 sm:w-auto w-full sm:h-auto sm:bg-transparent bg-slate-900/95 sm:backdrop-blur-none backdrop-blur-xl
            sm:flex sm:flex-row flex-col sm:items-center gap-2 sm:gap-6 px-4 sm:px-0 transition-all duration-300 border-b sm:border-0 border-white/10
            ${navbarOpen ? "max-h-96 py-3" : "max-h-0 py-0 overflow-hidden sm:max-h-none"}`}
        >
          {navItems.map((item) => {
            const active = path === item.path;
            return (
              <li key={item.path} className="relative py-1">
                <Link
                  to={item.path}
                  className={`text-sm font-medium transition-colors duration-200 ${
                    active ? "text-white" : "text-slate-300 hover:text-white"
                  }`}
                  onClick={() => setNavbarOpen(false)}
                >
                  {item.label}
                </Link>
                {active && (
                  <Motion.span
                    layoutId="nav-underline"
                    className="absolute -bottom-1 left-0 h-0.5 w-full bg-gradient-to-r from-blue-400 to-purple-500"
                  />
                )}
              </li>
            );
          })}

          {!token && (
            <li className="sm:ml-2">
              <Link to="/register" onClick={() => setNavbarOpen(false)}>
                <Motion.span
                  whileHover={{ scale: 1.05 }}
                  whileTap={{ scale: 0.95 }}
                  className="inline-block btn-gradient text-sm"
                >
                  Sign Up
                </Motion.span>
              </Link>
            </li>
          )}

          {token && (
            <li className="sm:ml-2">
              <Motion.button
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={onLogOutHandler}
                className="bg-rose-600/90 text-white text-sm font-semibold rounded-xl px-4 py-2 shadow-lg shadow-rose-700/30"
              >
                Log Out
              </Motion.button>
            </li>
          )}
        </ul>

        <button
          onClick={() => setNavbarOpen((prev) => !prev)}
          className="sm:hidden flex items-center"
          aria-label="Toggle navigation"
        >
          {navbarOpen ? (
            <RxCross2 className="text-white text-3xl" />
          ) : (
            <IoIosMenu className="text-white text-3xl" />
          )}
        </button>
      </div>
    </header>
  );
};

export default Navbar;