/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./features/**/*.{js,ts,jsx,tsx,mdx}",
    "./lib/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        teal: {
          50: "#effcfb",
          100: "#d9f6f3",
          200: "#b9ece8",
          300: "#82ddd7",
          400: "#52ccc4",
          500: "#35c5bd",
          600: "#239f99",
          700: "#1d817b",
          800: "#17645f",
          900: "#124b48",
        },
      },
    },
  },
  plugins: [],
};
