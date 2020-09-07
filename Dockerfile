FROM openjdk:11 AS builder
ARG SBT_VERSION=1.3.13

# Install sbt
RUN \
  curl -L -o sbt-$SBT_VERSION.deb https://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb && \
  dpkg -i sbt-$SBT_VERSION.deb && \
  rm sbt-$SBT_VERSION.deb && \
  apt-get update && \
  apt-get install sbt && \
  sbt sbtVersion

WORKDIR /code
COPY . /code
RUN sbt assembly


FROM openjdk:11-slim
COPY --from=builder /code/target/scala-**/bus-info-app.jar app.jar
EXPOSE 80
ENTRYPOINT ["java","-jar","/app.jar"]