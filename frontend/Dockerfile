# SPA phục vụ bằng Caddy; Caddy cũng chuyển /api/* sang Gateway và tự lấy HTTPS. Build từ thư mục gốc:
#   docker build -t openevent/web frontend
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# API gọi cùng domain với SPA (Caddy chuyển /api/* sang Gateway) nên baseURL để trống: không cần CORS.
ENV VITE_API_URL=""
RUN npm run build

FROM caddy:2-alpine
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /app/dist /srv
