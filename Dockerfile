FROM  eed3si9n/sbt:jdk11-alpine AS builder

WORKDIR /code
COPY . /code
RUN sbt "project api" assembly


FROM openjdk:11-slim
COPY --from=builder /code/modules/api/target/scala-**/bus-info-app.jar app.jar
EXPOSE 80
ENTRYPOINT ["java","-jar","/app.jar"]