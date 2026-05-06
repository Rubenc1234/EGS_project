FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw

COPY src ./src

RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl jq \
	&& rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar /app/app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 8081

ENTRYPOINT ["/app/entrypoint.sh"]
