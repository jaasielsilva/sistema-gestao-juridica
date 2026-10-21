# syntax=docker/dockerfile:1
FROM node:25-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.31-alpine
# Aplica correcoes de seguranca do Alpine publicadas depois da imagem base.
RUN apk upgrade --no-cache
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf nginx/locations.conf /etc/nginx/snippets/
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
