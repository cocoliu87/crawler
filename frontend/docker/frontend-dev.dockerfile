#
# DEVELOPMENT SERVER
#
FROM node:18-alpine
WORKDIR /app
COPY . .
RUN npm install next && npm install
EXPOSE 3000
ENTRYPOINT ["npm", "run", "dev"]
