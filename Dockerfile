FROM sbtscala/scala-sbt:graalvm-ce-22.3.0-b2-java17_1.8.2_2.13.10 AS builder

WORKDIR /code
COPY . /code
RUN sbt "project api" stage


FROM openjdk:17-slim
COPY --from=builder /code/modules/api/target/universal/stage/ /app
EXPOSE 80
ENTRYPOINT ["/app/bin/api"]