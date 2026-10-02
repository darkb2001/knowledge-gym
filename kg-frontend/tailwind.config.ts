import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./app/**/*.{js,ts,jsx,tsx,mdx}", "./components/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
        canvas: "#f3eee4",
        surface: "#fffcf6",
        sand: "#e8dfcd",
        muted: "#ede7db",
        line: "#d5cdbf",
        control: "#7d877f",
        strong: "#273c4a",
        body: "#43525a",
        subtle: "#59635f",
        accent: { DEFAULT: "#345f73", hover: "#284d60", soft: "#e3edf0" },
        sage: "#dce7d9",
        positive: "#386345",
        warning: "#99512e",
        danger: "#a33e35",
        "on-accent": "#fffcf6",
        // Compatibility for server-authored answer HTML, not application UI tokens.
        ink: { 950: "#f3eee4", 900: "#fffcf6", 800: "#ede7db", 700: "#d5cdbf", 600: "#59635f", 500: "#59635f", 400: "#59635f", 300: "#43525a", 200: "#43525a", 100: "#273c4a", 50: "#273c4a" },
        moss: { 700: "#345f73", 600: "#386345", 500: "#386345", 400: "#386345", 300: "#386345" },
        ember: { 500: "#345f73", 400: "#99512e", 300: "#99512e" },
      },
      fontFamily: {
        display: ["var(--font-geist)", "system-ui", "sans-serif"],
        sans: ["var(--font-geist)", "system-ui", "sans-serif"],
        mono: ["var(--font-geist-mono)", "ui-monospace", "monospace"],
      },
      borderRadius: { sm: "8px" },
      keyframes: {
        "fade-up": { "0%": { opacity: "0.85", transform: "translateY(6px)" }, "100%": { opacity: "1", transform: "translateY(0)" } },
      },
      animation: {
        "fade-up": "fade-up 240ms cubic-bezier(0.16,1,0.3,1) both",
        "fade-up-delay": "fade-up 240ms cubic-bezier(0.16,1,0.3,1) both",
        "soft-pulse": "none",
      },
    },
  },
  plugins: [],
};
export default config;
