name := "bus-info"

version := "0.1"
scalaVersion := "2.13.3"
organization := "dev.faustin0"

val http4sVersion              = "0.21.7"
val testcontainersScalaVersion = "0.38.3"
val catsVersion                = "2.2.0"

val testDependencies = Seq(
  "org.scalatest"  %% "scalatest"                       % "3.2.2"                    % Test,
  "com.dimafeng"   %% "testcontainers-scala-dynalite"   % testcontainersScalaVersion % Test,
  "com.dimafeng"   %% "testcontainers-scala-localstack" % testcontainersScalaVersion % Test,
  "com.dimafeng"   %% "testcontainers-scala-scalatest"  % testcontainersScalaVersion % Test,
  "com.codecommit" %% "cats-effect-testing-scalatest"   % "0.4.1"                    % Test,
  "org.typelevel"  %% "cats-effect-laws"                % catsVersion                % Test
)

libraryDependencies ++= Seq(
  "org.typelevel"          %% "cats-effect"            % catsVersion,
  "org.http4s"             %% "http4s-dsl"             % http4sVersion,
  "org.http4s"             %% "http4s-blaze-server"    % http4sVersion,
  "org.http4s"             %% "http4s-blaze-client"    % http4sVersion,
  "org.http4s"             %% "http4s-scala-xml"       % http4sVersion,
  "org.http4s"             %% "http4s-circe"           % http4sVersion,
  "io.circe"               %% "circe-generic"          % "0.13.0",
  "org.scala-lang.modules" %% "scala-xml"              % "1.3.0",
  "com.amazonaws"           % "aws-lambda-java-core"   % "1.2.1",
  "com.amazonaws"           % "aws-java-sdk-s3"        % "1.11.873",
  "com.amazonaws"           % "aws-lambda-java-events" % "3.3.1",
  "com.amazonaws"           % "aws-java-sdk-dynamodb"  % "1.11.873",
  "ch.qos.logback"          % "logback-classic"        % "1.2.3" % Runtime
) ++ testDependencies

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps",
  "-language:higherKinds",
  "-Xfatal-warnings"
)

Test / fork := true

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case _                             => MergeStrategy.first
}
test in assembly := {} //no tests in assembly
assemblyJarName in assembly := "bus-info-app.jar"
