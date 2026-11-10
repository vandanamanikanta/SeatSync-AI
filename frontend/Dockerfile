# ==============================================================================
# SeatSync AI - Frontend Multi-Stage Dockerfile
# Stage 1: Build with Node 22 Alpine, TypeScript & Vite
# Stage 2: Serve static SPA & Reverse Proxy with NGINX Alpine
# ==============================================================================

# --- Stage 1: Build Stage ---
FROM node:22-alpine AS builder

WORKDIR /app

# Copy dependency specifications first for Docker layer caching
COPY package.json package-lock.json ./

# Install exact dependencies
RUN npm ci

# Copy frontend source code
COPY . .

# Compile TypeScript and build production bundle into /app/dist
RUN npm run build

# --- Stage 2: Production NGINX Stage ---
FROM nginx:1.27-alpine AS runner

# Remove default NGINX welcome page
RUN rm -rf /usr/share/nginx/html/*

# Copy custom NGINX configuration for SPA routing and reverse proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy compiled static assets from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Expose HTTP port
EXPOSE 80

# Health check to ensure NGINX is actively serving traffic
HEALTHCHECK --interval=20s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost:80/ || exit 1

# Start NGINX in foreground
CMD ["nginx", "-g", "daemon off;"]

