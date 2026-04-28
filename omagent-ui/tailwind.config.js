/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#1890FF',
          dark: '#096DD9',
          deeper: '#0050B3',
        },
        surface: {
          DEFAULT: '#1F1F1F',
          light: '#2A2A2A',
          lighter: '#333333',
        },
        success: '#52C41A',
        warning: '#FAAD14',
        danger: '#FA541C',
      },
      fontFamily: {
        sans: ['PingFang-SC', 'Microsoft YaHei', 'sans-serif'],
      },
    },
  },
  plugins: [
    require('tailwindcss-animate'),
  ],
}
