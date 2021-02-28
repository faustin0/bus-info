import sbt._

object Dependencies {
  val http4sVersion              = "0.21.19"
  val testcontainersScalaVersion = "0.39.1"
  val catsVersion                = "2.3.3"
  val kindProjectorV             = "0.11.3"
  val betterMonadicForV          = "0.3.1"
  val circeVersion               = "0.13.0"
  val scalaXmlVersion            = "1.3.0"
  val log4catsVersion            = "1.1.1"
  val awsLambdaVersion           = "1.2.1"
  val s3sdkVersion               = "1.11.959"
  val awsLambdaJavaEventsVersion = "3.7.0"
  val tapirVersion               = "0.17.10"
  val dynamodbVersion            = "1.11.959"
  val logbackVersion             = "1.2.3"

  lazy val testDependencies = Seq(
    "org.scalatest"  %% "scalatest"                          % "3.2.5"                    % Test,
    "com.dimafeng"   %% "testcontainers-scala-dynalite"      % testcontainersScalaVersion % Test,
    "com.dimafeng"   %% "testcontainers-scala-localstack-v2" % testcontainersScalaVersion % Test,
    "com.dimafeng"   %% "testcontainers-scala-scalatest"     % testcontainersScalaVersion % Test,
    "com.codecommit" %% "cats-effect-testing-scalatest"      % "0.5.1"                    % Test,
    "org.typelevel"  %% "cats-effect-laws"                   % catsVersion                % Test
  )

  lazy val dependencies = Seq(
    "org.typelevel"          %% "cats-effect"           % catsVersion,
    "io.circe"               %% "circe-generic"         % circeVersion,
    "org.scala-lang.modules" %% "scala-xml"             % scalaXmlVersion,
    "io.chrisdavenport"      %% "log4cats-slf4j"        % log4catsVersion,
    "com.amazonaws"           % "aws-java-sdk-dynamodb" % dynamodbVersion,
    "ch.qos.logback"          % "logback-classic"       % logbackVersion % Runtime
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

//  lazy val lambdaDeps = Seq(
//    "com.amazonaws" % "aws-lambda-java-core"   % awsLambdaVersion,
//    "com.amazonaws" % "aws-java-sdk-s3"        % s3sdkVersion,
//    "com.amazonaws" % "aws-lambda-java-events" % awsLambdaJavaEventsVersion
//  )

  lazy val awsDeps = Seq(
    "software.amazon.awssdk" % "s3"                     % "2.16.8",
    "com.amazonaws"          % "aws-lambda-java-core"   % "1.2.1",
    "com.amazonaws"          % "aws-lambda-java-events" % "3.7.0"
  )

}
