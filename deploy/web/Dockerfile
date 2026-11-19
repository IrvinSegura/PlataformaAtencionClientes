# The public face of the platform: Caddy (TLS, HTTP/3, reverse proxy to the backend) with the panel and widget.js
# built in. Build from the repository root: docker build -f deploy/web/Dockerfile --build-arg DOMAIN=... .
# The frontends are built on the builder's own platform; only the final Caddy layer is arm64.

FROM --platform=$BUILDPLATFORM node:24-alpine AS widget
WORKDIR /widget
COPY widget/package.json widget/package-lock.json ./
RUN npm ci
COPY widget/ ./
RUN npm run build

FROM --platform=$BUILDPLATFORM node:24-alpine AS panel
# Vite inlines these into the bundle: the snippet the panel shows points at this same domain
ARG DOMAIN
RUN test -n "$DOMAIN" || (echo "DOMAIN build argument is required" && false)
ENV VITE_API_BASE_URL="" \
    VITE_WIDGET_SCRIPT_URL="https://${DOMAIN}/widget.js" \
    VITE_WIDGET_API_URL="https://${DOMAIN}"
WORKDIR /panel
COPY panel/package.json panel/package-lock.json ./
RUN npm ci
COPY panel/ ./
RUN npm run build

FROM caddy:2.10-alpine
COPY deploy/web/Caddyfile /etc/caddy/Caddyfile
COPY --from=panel /panel/dist /srv/panel
COPY --from=widget /widget/dist/widget.js /srv/widget/widget.js
COPY deploy/web/demo /srv/demo
