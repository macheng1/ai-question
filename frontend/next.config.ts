import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  async rewrites() {
    // 由 Next.js 转发，浏览器无需访问内网后端。
    const origin = (process.env.API_PROXY_ORIGIN || "http://localhost:18080").replace(/\/+$/, "");
    return [{ source: "/api/:path*", destination: `${origin}/api/:path*` }];
  },
};

export default nextConfig;
