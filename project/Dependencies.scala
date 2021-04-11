import sbt._

object Dependencies {
  val http4sVersion              = "0.21.22"
  val testcontainersScalaVersion = "0.39.3"
  val catsVersion                = "2.4.1"
  val kindProjectorV             = "0.11.3"
  val betterMonadicForV          = "0.3.1"
  val circeVersion               = "0.13.0"
  val scalaXmlVersion            = "1.3.0"
  val log4catsVersion            = "1.1.1"
  val awsSdkVersion              = "2.16.34"
  val tapirVersion               = "0.17.19"
  val logbackVersion             = "1.2.3"

  lazy val testDependencies = Seq(
    "org.scalatest"  %% "scalatest"                          % "3.2.7"                    % Test,
    "com.dimafeng"   %% "testcontainers-scala-localstack-v2" % testcontainersScalaVersion % Test,
    "com.amazonaws"   % "aws-java-sdk"                       % "1.11.991"                 % Test, //needed by localstack
    "com.dimafeng"   %% "testcontainers-scala-scalatest"     % testcontainersScalaVersion % Test,
    "com.codecommit" %% "cats-effect-testing-scalatest"      % "0.5.2"                    % Test,
    "org.typelevel"  %% "cats-effect-laws"                   % catsVersion                % Test
  )

  lazy val dependencies = Seq(
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
    "com.amazonaws"          % "aws-lambda-java-events" % "3.8.0",
    "software.amazon.awssdk" % "s3"                     % awsSdkVersion
  ) ++ dynamoDeps

  lazy val dynamoDeps = Seq(
    "software.amazon.awssdk" % "dynamodb" % awsSdkVersion
  )

}
