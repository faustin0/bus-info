import sbt._

object Dependencies {
  val http4sVersion        = "0.21.24"
  val testcontainersScalaV = "0.39.5"
  val catsVersion          = "2.5.1"
  val kindProjectorV       = "0.13.0"
  val betterMonadicForV    = "0.3.1"
  val circeVersion         = "0.14.1"
  val scalaXmlVersion      = "1.3.0"
  val log4catsVersion      = "1.1.1"
  val awsSdkVersion        = "2.16.99"
  val tapirVersion         = "0.18.0-M15"
  val logbackVersion       = "1.2.3"
  val fs2Version           = "3.0.6"

  lazy val testDependencies = Seq(
    "org.scalatest"  %% "scalatest"                          % "3.2.9"              % Test,
    "com.dimafeng"   %% "testcontainers-scala-localstack-v2" % testcontainersScalaV % Test,
    "com.amazonaws"   % "aws-java-sdk"                       % "1.12.21"          % Test, //needed by localstack
    "com.dimafeng"   %% "testcontainers-scala-scalatest"     % testcontainersScalaV % Test,
    "com.codecommit" %% "cats-effect-testing-scalatest"      % "0.5.4"              % Test,
    "org.typelevel"  %% "cats-effect-laws"                   % catsVersion          % Test
  )

  lazy val dependencies = Seq(
    "co.fs2"                 %% "fs2-core"        % fs2Version,
    "org.typelevel"          %% "cats-effect"     % catsVersion,
    "io.circe"               %% "circe-generic"   % circeVersion,
    "org.scala-lang.modules" %% "scala-xml"       % scalaXmlVersion,
    "io.chrisdavenport"      %% "log4cats-slf4j"  % log4catsVersion,
    "ch.qos.logback"          % "logback-classic" % logbackVersion % Runtime
  )

  lazy val httpClientDeps = Seq(
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-scala-xml"    % http4sVersion
  )

  lazy val httpServerDeps = Seq(
    "org.http4s"                  %% "http4s-dsl"               % http4sVersion,
    "org.http4s"                  %% "http4s-blaze-server"      % http4sVersion,
    "org.http4s"                  %% "http4s-scala-xml"         % http4sVersion,
    "org.http4s"                  %% "http4s-circe"             % http4sVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-core"               % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"      % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"       % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s"  % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe"         % tapirVersion
  )

  lazy val awsDeps = Seq(
    "com.amazonaws"          % "aws-lambda-java-core"   % "1.2.1",
    "com.amazonaws"          % "aws-lambda-java-events" % "3.9.0",
    "software.amazon.awssdk" % "s3"                     % awsSdkVersion
  ) ++ dynamoDeps

  lazy val dynamoDeps = Seq(
    "software.amazon.awssdk" % "dynamodb" % awsSdkVersion
  )

}
