FROM node:20-alpine

WORKDIR /app

# Copy server package definitions
COPY server/package*.json ./

RUN npm ci --only=production

# Copy server application code
COPY server/ ./

EXPOSE 8080

ENV PORT=8080
ENV NODE_ENV=production

CMD ["node", "server.js"]
