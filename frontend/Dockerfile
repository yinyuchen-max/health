FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginx:1.27-alpine
# 自签兜底证书：保证未签发 Let's Encrypt 证书前 nginx 也能启动 443
RUN apk add --no-cache openssl \
    && mkdir -p /etc/nginx/ssl \
    && openssl req -x509 -nodes -newkey rsa:2048 -days 3650 \
       -keyout /etc/nginx/ssl/fallback.key \
       -out /etc/nginx/ssl/fallback.crt \
       -subj "/CN=zpew.top"
COPY nginx.conf /etc/nginx/conf.d/default.conf
# 官方镜像启动时会自动执行 /docker-entrypoint.d/*.sh，用于切换正式/兜底证书
COPY --chmod=755 docker-entrypoint.d/40-ssl-symlink.sh /docker-entrypoint.d/40-ssl-symlink.sh
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80 443
