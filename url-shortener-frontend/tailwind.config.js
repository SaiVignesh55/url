/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      backgroundImage: {
        "custom-gradient": "linear-gradient(to right, #3b82f6, #9333ea)", // equivalent to from-blue-500 to-purple-600
        "custom-gradient-2": "linear-gradient(to left, #3b82f6, #f43f5e)",
        "card-gradient": "linear-gradient(to right, #38b2ac, #4299e1)",
      },
      colors: {
        navbarColor: "#ffffff",
        btnColor: "#3364F7",
        linkColor: "#2a5bd7",
      },
      boxShadow: {
        custom: "0 0 15px rgba(0, 0, 0, 0.3)",
        right: "10px 0px 10px -5px rgba(0, 0, 0, 0.3)",
        glass: "0 16px 50px rgba(12, 18, 36, 0.35)",
        glowBlue: "0 0 28px rgba(59, 130, 246, 0.45)",
        glowGreen: "0 0 24px rgba(34, 197, 94, 0.45)",
        glowAmber: "0 0 24px rgba(245, 158, 11, 0.45)",
        glowRed: "0 0 26px rgba(239, 68, 68, 0.45)",
      },
      keyframes: {
        gradientShift: {
          "0%": { backgroundPosition: "0% 50%" },
          "50%": { backgroundPosition: "100% 50%" },
          "100%": { backgroundPosition: "0% 50%" },
        },
        floatSlow: {
          "0%, 100%": { transform: "translateY(0px)" },
          "50%": { transform: "translateY(-10px)" },
        },
      },
      animation: {
        gradientShift: "gradientShift 16s ease infinite",
        floatSlow: "floatSlow 6s ease-in-out infinite",
      },
      fontFamily: {
        roboto: ["Roboto", "sans-serif"],
        montserrat: ["Montserrat"],
      },
    },
  },
  plugins: [],
};