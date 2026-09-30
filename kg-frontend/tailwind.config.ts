import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        ink: {
          950: "#0c1210",
          900: "#121a17",
          800: "#1a2621",
          700: "#24332c",
          600: "#3a4f45",
          400: "#7a9286",
          200: "#c5d4cc",
          100: "#e8efeb",
          50: "#f4f7f5",
        },
        ember: {
          500: "#e07a3d",
          400: "#f09a5c",
          300: "#f5b888",
        },
        moss: {
          600: "#2f6b4f",
          500: "#3d8a64",
          400: "#5aad7f",
        },
      },
      fontFamily: {
        display: ["var(--font-display)", "Georgia", "serif"],
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
      },
      backgroundImage: {
        "desk-grain":
          "radial-gradient(ellipse at 20% 0%, rgba(61,138,100,0.18), transparent 50%), radial-gradient(ellipse at 90% 10%, rgba(224,122,61,0.12), transparent 45%), linear-gradient(165deg, #0c1210 0%, #121a17 40%, #1a2621 100%)",
      },
      keyframes: {
        "fade-up": {
          "0%": { opacity: "0", transform: "translateY(12px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "soft-pulse": {
          "0%, 100%": { opacity: "0.55" },
          "50%": { opacity: "1" },
        },
      },
      animation: {
        "fade-up": "fade-up 0.45s ease-out both",
        "fade-up-delay": "fade-up 0.55s ease-out 0.08s both",
        "soft-pulse": "soft-pulse 2.4s ease-in-out infinite",
      },
    },
  },
  plugins: [],
};
export default config;
