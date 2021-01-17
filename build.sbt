val http4sVersion = "0.21.15"

val testcontainersScalaVersion = "0.38.8"

val catsVersion = "2.3.1"

val kindProjectorV = "0.11.2"

val betterMonadicForV = "0.3.1"

val circeVersion = "0.13.0"

val scalaXmlVersion = "1.3.0"

val log4catsVersion = "1.1.1"

val awsLambdaVersion = "1.2.1"

val s3sdkVersion = "1.11.930"

val awsLambdaJavaEventsVersion = "3.7.0"

val dynamodbVersion = "1.11.930"

val tapirVersion = "0.17.4"

val logbackVersion = "1.2.3"

inThisBuild(
  List(
    organization := "dev.faustin0",
    developers := List(
      Developer("faustin0", "Fausto Di Natale", "", url("https://github.com/faustin0")),
      Developer("azanin", "Alessandro Zanin", "ale.zanin90@gmail.com", url("https://github.com/azanin"))
    ),
    homepage := Some(url("https://github.com/faustin0/bus-info")),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    pomIncludeRepository := { _ => false }
  )
)

// General Settings
lazy val commonSettings = Seq(
  scalaVersion := "2.13.3",
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % kindProjectorV cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % betterMonadicForV),
  scalacOptions ++= Seq(
    "-feature",
    "-deprecation",
    "-unchecked",
    "-language:postfixOps",
    "-language:higherKinds",
    "-Xfatal-warnings"
  ),
  libraryDependencies ++= dependencies ++ testDependencies
)

lazy val core = project
  .in(file("."))
  .settings(commonSettings)
  .settings(name := "bus-info")
  .settings(parallelExecution in Test := false)
  .settings(fork in Test := true)
  .settings(assemblyMergeStrategy in assembly := {
    case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") => MergeStrategy.singleOrError
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  })
  .settings(test in assembly := {})
  .settings(assemblyJarName in assembly := "bus-info-app.jar")

val testDependencies = Seq(
  "org.scalatest"  %% "scalatest"                       % "3.2.3"                    % Test,
  "com.dimafeng"   %% "testcontainers-scala-dynalite"   % testcontainersScalaVersion % Test,
  "com.dimafeng"   %% "testcontainers-scala-localstack" % testcontainersScalaVersion % Test,
  "com.dimafeng"   %% "testcontainers-scala-scalatest"  % testcontainersScalaVersion % Test,
  "com.codecommit" %% "cats-effect-testing-scalatest"   % "0.5.0"                    % Test,
  "org.typelevel"  %% "cats-effect-laws"                % catsVersion                % Test
)

val dependencies = Seq(
  "org.typelevel"               %% "cats-effect"              % catsVersion,
  "org.http4s"                  %% "http4s-dsl"               % http4sVersion,
  "org.http4s"                  %% "http4s-blaze-server"      % http4sVersion,
  "org.http4s"                  %% "http4s-blaze-client"      % http4sVersion,
  "org.http4s"                  %% "http4s-scala-xml"         % http4sVersion,
  "org.http4s"                  %% "http4s-circe"             % http4sVersion,
  "io.circe"                    %% "circe-generic"            % circeVersion,
  "org.scala-lang.modules"      %% "scala-xml"                % scalaXmlVersion,
  "io.chrisdavenport"           %% "log4cats-slf4j"           % log4catsVersion,
  "com.amazonaws"               % "aws-lambda-java-core"      % awsLambdaVersion,
  "com.amazonaws"               % "aws-java-sdk-s3"           % s3sdkVersion,
  "com.amazonaws"               % "aws-lambda-java-events"    % awsLambdaJavaEventsVersion,
  "com.amazonaws"               % "aws-java-sdk-dynamodb"     % dynamodbVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-core"               % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"      % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"       % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-http4s"  % tapirVersion,
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe"         % tapirVersion,
  "ch.qos.logback"              % "logback-classic"           % logbackVersion % Runtime
)
