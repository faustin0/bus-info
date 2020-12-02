FROM  eed3si9n/sbt:jdk11-alpine AS builder

WORKDIR /code
COPY . /code
RUN sbt assembly


FROM openjdk:11-slim
COPY --from=builder /code/target/scala-**/bus-info-app.jar app.jar
EXPOSE 80
ENTRYPOINT ["java","-jar","/app.jar"]