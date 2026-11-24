FROM m.daocloud.io/docker.io/library/node:22-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm config set registry https://registry.npmmirror.com \
    && npm ci
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL=/api
ARG API_PROXY_ORIGIN=http://10.1.12.70:18080
ENV API_PROXY_ORIGIN=$API_PROXY_ORIGIN
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL
RUN npm run build

FROM m.daocloud.io/docker.io/library/node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
ARG API_PROXY_ORIGIN=http://10.1.12.70:18080
ENV API_PROXY_ORIGIN=$API_PROXY_ORIGIN
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/public ./public
COPY --from=builder /app/package*.json ./
COPY --from=builder /app/next.config.ts ./
RUN npm config set registry https://registry.npmmirror.com \
    && npm ci --omit=dev
EXPOSE 3000
CMD ["npm", "run", "start"]
