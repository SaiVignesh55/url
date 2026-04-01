import React, { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { motion as Motion } from "framer-motion";
import api from "../api/api";
import toast from "react-hot-toast";

const RegisterPage = () => {
  const navigate = useNavigate();
  const [loader, setLoader] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm({
    defaultValues: {
      username: "",
      email: "",
      password: "",
    },
    mode: "onTouched",
  });

  const registerHandler = async (data) => {
    setLoader(true);
    try {
      await api.post("/api/auth/public/register", data);
      reset();
      navigate("/login");
      toast.success("Registeration Successful!");
    } catch (error) {
      console.log(error);
      toast.error("Registeration Failed!");
    } finally {
      setLoader(false);
    }
  };

  return (
    <div className="min-h-[calc(100vh-64px)] flex justify-center items-center px-4 py-8 relative overflow-hidden">
      <div className="absolute -top-16 -right-12 w-56 h-56 rounded-full bg-blue-500/25 blur-3xl animate-floatSlow" />
      <div className="absolute -bottom-14 -left-10 w-56 h-56 rounded-full bg-purple-500/25 blur-3xl animate-floatSlow" />

      <Motion.form
        initial={{ opacity: 0, y: 28 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        onSubmit={handleSubmit(registerHandler)}
        className="sm:w-[460px] w-full max-w-[460px] glass-card p-6 sm:p-8 ring-1 ring-purple-300/30"
      >
        <h1 className="text-center font-serif text-white font-bold lg:text-3xl text-2xl">
          Create Account
        </h1>
        <p className="text-center text-slate-300 text-sm mt-1">
          Start managing and scanning links in one place.
        </p>

        <div className="mt-6 flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <label
              htmlFor="username"
              className="font-semibold text-sm text-slate-100"
            >
              Username
            </label>
            <input
              id="username"
              type="text"
              placeholder="Type your username"
              className={`px-3 py-2.5 rounded-xl border bg-slate-900/40 text-slate-100 placeholder:text-slate-400 outline-none transition-all duration-300 focus:border-blue-400 focus:shadow-glowBlue ${
                errors.username ? "border-red-400" : "border-white/20"
              }`}
              {...register("username", { required: "*Username is required" })}
            />
            {errors.username?.message && (
              <p className="text-xs text-red-300 font-semibold">
                {errors.username.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1">
            <label
              htmlFor="email"
              className="font-semibold text-sm text-slate-100"
            >
              Email
            </label>
            <input
              id="email"
              type="email"
              placeholder="Type your email"
              className={`px-3 py-2.5 rounded-xl border bg-slate-900/40 text-slate-100 placeholder:text-slate-400 outline-none transition-all duration-300 focus:border-blue-400 focus:shadow-glowBlue ${
                errors.email ? "border-red-400" : "border-white/20"
              }`}
              {...register("email", {
                required: "*Email is required",
                pattern: {
                  value: /^[a-zA-Z0-9]+@(?:[a-zA-Z0-9]+\.)+com+$/,
                  message: "Invalid email",
                },
              })}
            />
            {errors.email?.message && (
              <p className="text-xs text-red-300 font-semibold">
                {errors.email.message}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-1">
            <label
              htmlFor="password"
              className="font-semibold text-sm text-slate-100"
            >
              Password
            </label>
            <input
              id="password"
              type="password"
              placeholder="Type your password"
              className={`px-3 py-2.5 rounded-xl border bg-slate-900/40 text-slate-100 placeholder:text-slate-400 outline-none transition-all duration-300 focus:border-blue-400 focus:shadow-glowBlue ${
                errors.password ? "border-red-400" : "border-white/20"
              }`}
              {...register("password", {
                required: "*Password is required",
                minLength: {
                  value: 6,
                  message: "Minimum 6 character is required",
                },
              })}
            />
            {errors.password?.message && (
              <p className="text-xs text-red-300 font-semibold">
                {errors.password.message}
              </p>
            )}
          </div>
        </div>

        <Motion.button
          disabled={loader}
          type="submit"
          whileHover={{ scale: loader ? 1 : 1.05 }}
          whileTap={{ scale: loader ? 1 : 0.95 }}
          className="btn-gradient w-full mt-5 disabled:opacity-70"
        >
          {loader ? "Loading..." : "Register"}
        </Motion.button>

        <p className="text-center text-sm text-slate-300 mt-6">
          Already have an account?
          <Link
            className="font-semibold underline hover:text-white ml-1"
            to="/login"
          >
            <span className="text-blue-300">Login</span>
          </Link>
        </p>
      </Motion.form>
    </div>
  );
};

export default RegisterPage;
