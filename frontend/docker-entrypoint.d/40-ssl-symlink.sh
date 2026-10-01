#!/bin/sh
# 容器启动时选择 TLS 证书：
# 1. 若已签发 Let's Encrypt 证书（挂载卷中存在），符号链接指向正式证书
# 2. 否则指向镜像内置的自签兜底证书，保证 nginx 在任何阶段都能启动
set -e

LE_DIR=/etc/letsencrypt/live/zpew.top
SSL_DIR=/etc/nginx/ssl

mkdir -p "$SSL_DIR"

if [ -f "$LE_DIR/fullchain.pem" ] && [ -f "$LE_DIR/privkey.pem" ]; then
    ln -sf "$LE_DIR/fullchain.pem" "$SSL_DIR/fullchain.pem"
    ln -sf "$LE_DIR/privkey.pem" "$SSL_DIR/privkey.pem"
    echo "[ssl] using Let's Encrypt certificate for zpew.top"
else
    ln -sf "$SSL_DIR/fallback.crt" "$SSL_DIR/fullchain.pem"
    ln -sf "$SSL_DIR/fallback.key" "$SSL_DIR/privkey.pem"
    echo "[ssl] Let's Encrypt certificate not found, using self-signed fallback"
fi
