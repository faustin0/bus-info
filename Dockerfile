FROM  eed3si9n/sbt:jdk11-alpine AS builder

WORKDIR /code
COPY . /code
RUN sbt "project api" stage


FROM openjdk:17-slim
COPY --from=builder /code/modules/api/target/universal/stage/ /app
EXPOSE 80
ENTRYPOINT ["/app/bin/api"]