import sbt._

object Dependencies {
  val http4sVersion = "0.23.16"

  val catsVersion = "3.4.4"

  val testcontainersScalaV = "0.40.12"

  val kindProjectorV = "0.13.2"

  val betterMonadicForV = "0.3.1"

  val circeVersion = "0.14.3"

  val scalaXmlVersion = "2.1.0"

  val log4catsVersion = "2.5.0"

  val tapirVersion = "1.0.0-M9"

  val awsSdkVersion = "2.19.8"

  val fs2Version = "3.4.0"

  val log4j2Version = "2.19.0"

  val xs4sVersion = "0.9.1"

  lazy val testDependencies = Seq(
    "org.scalatest" %% "scalatest"                          % "3.2.14"             % Test,
    "com.dimafeng"  %% "testcontainers-scala-localstack-v2" % testcontainersScalaV % Test,
    "com.amazonaws"  % "aws-java-sdk"                       % "1.12.376"           % Test, // needed by localstack
    "com.dimafeng"  %% "testcontainers-scala-scalatest"     % testcontainersScalaV % Test,
    "org.typelevel" %% "cats-effect-testing-scalatest"      % "1.5.0"              % Test,
    "org.typelevel" %% "cats-effect-laws"                   % catsVersion          % Test
  )

  lazy val dependencies = Seq(
    "co.fs2"                  %% "fs2-core"                   % fs2Version,
    "org.typelevel"           %% "cats-effect"                % catsVersion,
    "io.circe"                %% "circe-generic"              % circeVersion,
    "org.scala-lang.modules"  %% "scala-xml"                  % scalaXmlVersion,
    "org.typelevel"           %% "log4cats-slf4j"             % log4catsVersion,
    "org.apache.logging.log4j" % "log4j-layout-template-json" % log4j2Version % Runtime,
    "org.apache.logging.log4j" % "log4j-api"                  % log4j2Version % Runtime,
    "org.apache.logging.log4j" % "log4j-slf4j-impl"           % log4j2Version % Runtime
  )

  lazy val httpClientDeps = Seq(
    "org.http4s" %% "http4s-ember-client" % http4sVersion,
    "org.http4s" %% "http4s-scala-xml"    % "0.23.12"
  )

  lazy val httpServerDeps = Seq(
    "org.http4s"                  %% "http4s-dsl"               % http4sVersion,
    "org.http4s"                  %% "http4s-ember-server"      % http4sVersion,
    "org.http4s"                  %% "http4s-scala-xml"         % "0.23.12",
    "org.http4s"                  %% "http4s-circe"             % http4sVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-core"               % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"      % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"       % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui"         % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe"         % tapirVersion
  )

  lazy val awsDeps = Seq(
    "com.amazonaws"          % "aws-lambda-java-core"   % "1.2.2",
    "com.amazonaws"          % "aws-lambda-java-events" % "3.11.0",
    "software.amazon.awssdk" % "s3"                     % awsSdkVersion
  ) ++ dynamoDeps

  lazy val dynamoDeps = Seq(
    "software.amazon.awssdk" % "dynamodb" % awsSdkVersion
  )

  lazy val streamingXMl = Seq(
    "com.scalawilliam" %% "xs4s-fs2v3" % xs4sVersion
  )

}
