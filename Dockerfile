FROM node:24-alpine AS frontend
WORKDIR /web
RUN npm install --global pnpm@11.25.0
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /source
COPY pom.xml ./
COPY engine/ engine/
COPY solver/ solver/
COPY shared/ shared/
COPY api/ api/
COPY worker/ worker/
COPY --from=frontend /web/dist/ api/src/main/resources/static/
ARG MODULE=api
RUN mvn -B -pl ${MODULE} -am -DskipTests package && cp ${MODULE}/target/${MODULE}-1.0.0.jar /service.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /service.jar /app/service.jar
USER 10001:10001
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/service.jar"]
