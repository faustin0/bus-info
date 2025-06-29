import sbt.*

object Dependencies {
  val http4sVersion = "0.23.30"

  val catsVersion = "3.6.1"

  val testcontainersScalaV = "0.41.5"

  val kindProjectorV = "0.13.3"

  val betterMonadicForV = "0.3.1"

  val circeVersion = "0.14.14"

  val scalaXmlVersion = "2.4.0"

  val log4catsVersion = "2.7.0"

  val tapirVersion = "1.11.34"

  val awsSdkVersion = "2.26.20"

  val fs2Version = "3.12.0"

  val log4j2Version = "2.22.0"

  val xs4sVersion = "0.9.1"

  lazy val testDependencies = Seq(
    "org.scalatest" %% "scalatest"                          % "3.2.19"             % Test,
    "com.dimafeng"  %% "testcontainers-scala-localstack-v2" % testcontainersScalaV % Test,
    "com.amazonaws"  % "aws-java-sdk"                       % "1.12.787"           % Test, // needed by localstack
    "com.dimafeng"  %% "testcontainers-scala-scalatest"     % testcontainersScalaV % Test,
    "org.typelevel" %% "cats-effect-testing-scalatest"      % "1.6.0"              % Test,
    "org.typelevel" %% "cats-effect-laws"                   % catsVersion          % Test
  )

  lazy val munit = "org.typelevel" %% "munit-cats-effect" % "2.0.0" % Test

  lazy val dependencies = Seq(
    "co.fs2"                 %% "fs2-core"        % fs2Version,
    "org.typelevel"          %% "cats-effect"     % catsVersion,
    "io.circe"               %% "circe-generic"   % circeVersion,
    "org.scala-lang.modules" %% "scala-xml"       % scalaXmlVersion,
    "org.typelevel"          %% "log4cats-slf4j"  % log4catsVersion,
//    "org.apache.logging.log4j" % "log4j-layout-template-json" % log4j2Version % Runtime,
//    "org.apache.logging.log4j" % "log4j-api"                  % log4j2Version % Runtime,
//    "org.apache.logging.log4j" % "log4j-slf4j-impl"           % log4j2Version % Runtime,
    "org.slf4j"               % "jcl-over-slf4j"  % "2.0.13" % Runtime, // same version of slf4j used by log4cats
    "ch.qos.logback"          % "logback-classic" % "1.5.16" % Runtime
  )

  lazy val http4sClient: Seq[ModuleID] = Seq(
    "org.http4s" %% "http4s-ember-client" % http4sVersion,
    "org.http4s" %% "http4s-circe"        % http4sVersion,
    "org.http4s" %% "http4s-dsl"          % http4sVersion
  )

  lazy val http4sXML = "org.http4s" %% "http4s-scala-xml" % "0.23.15"

  lazy val httpServerDeps = Seq(
    "org.http4s"                  %% "http4s-dsl"              % http4sVersion,
    "org.http4s"                  %% "http4s-ember-server"     % http4sVersion,
    "org.http4s"                  %% "http4s-scala-xml"        % "0.23.15",
    "org.http4s"                  %% "http4s-circe"            % http4sVersion,
    "org.typelevel"               %% "feral-lambda-http4s"     % "0.3.0-RC2",
    "com.softwaremill.sttp.tapir" %% "tapir-core"              % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"     % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"      % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui"        % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % tapirVersion
  )

  lazy val lambdaRuntimeDeps = "com.amazonaws" % "aws-lambda-java-runtime-interface-client" % "2.4.1"

  lazy val awsLambdaDeps = Seq(
    "com.amazonaws" % "aws-lambda-java-core"   % "1.3.0",
    "com.amazonaws" % "aws-lambda-java-events" % "3.11.3"
  )

  @deprecated
  lazy val awsDeps = Seq(
    "com.amazonaws"          % "aws-lambda-java-core"   % "1.3.0",
    "com.amazonaws"          % "aws-lambda-java-events" % "3.16.0",
    "software.amazon.awssdk" % "s3"                     % awsSdkVersion
  ) ++ dynamoDeps

  lazy val dynamoDeps = Seq(
    "software.amazon.awssdk" % "dynamodb"              % awsSdkVersion, // todo exclude ("io.netty", "netty-nio-client") exclude ("software.amazon.awssdk", "apache-client"),
    "software.amazon.awssdk" % "url-connection-client" % awsSdkVersion
  )

  lazy val streamingXMl = "com.scalawilliam" %% "xs4s-fs2v3" % xs4sVersion

}
