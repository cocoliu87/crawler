#
# BUILD STAGE
#
FROM node:18-alpine as build
WORKDIR /app
COPY . .
RUN npm install next && npm install
RUN npm run build

#
# DEPLOYMENT STAGE
#
# production environment
FROM nginx:stable-alpine
COPY --from=build /app/out /usr/share/nginx/html
COPY --from=build /app/docker/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 8080
CMD ["nginx", "-g", "daemon off;"]
