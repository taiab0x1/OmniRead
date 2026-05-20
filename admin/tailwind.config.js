/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        bg: {
          0: "#0B0B0F",
          1: "#14141A",
          2: "#1E1E28",
          3: "#26263A",
        },
        accent: {
          DEFAULT: "#8B5CF6",
          soft: "#A78BFA",
          rose: "#EC4899",
        },
        ink: {
          0: "#FFFFFF",
          1: "#E5E5EE",
          2: "#9999B3",
          3: "#666680",
        },
        gold: "#F5C518",
        success: "#22C55E",
        warning: "#F59E0B",
        danger: "#EF4444",
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
        display: ["'Playfair Display'", "Georgia", "serif"],
      },
      borderRadius: {
        lg: "12px",
        xl: "16px",
        "2xl": "20px",
      },
      boxShadow: {
        card: "0 1px 0 rgba(255,255,255,0.04) inset, 0 8px 24px rgba(0,0,0,0.35)",
        focus: "0 0 0 3px rgba(139, 92, 246, 0.35)",
      },
    },
  },
  plugins: [],
};
