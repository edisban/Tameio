# The React app, built once and served by nginx, which also forwards /api to the backend.
FROM node:22.23.4-alpine AS build
WORKDIR /app
# Dependencies first, so a code change does not reinstall them. The cache mount keeps downloaded packages
# between builds on the same machine; npm retries a failed download by itself, five times here.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --fetch-retries=5
COPY . .
RUN npm run build

FROM nginx:1.31.8-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 5180
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD ["wget", "-q", "-O", "/dev/null", "http://127.0.0.1:5180/"]
