name := "bus-info"

version := "0.1"

scalaVersion := "2.13.2"

val http4sVersion = "0.21.4"
val testcontainersScalaVersion = "0.38.1"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "2.1.3" withSources () withJavadoc (),
  "org.http4s" %% "http4s-dsl" % http4sVersion withSources () withJavadoc (),
  "org.http4s" %% "http4s-blaze-server" % http4sVersion withSources () withJavadoc (),
  "org.http4s" %% "http4s-blaze-client" % http4sVersion withSources () withJavadoc (),
  "org.http4s" %% "http4s-scala-xml" % http4sVersion withSources () withJavadoc (),
  "org.http4s" %% "http4s-circe" % http4sVersion withSources () withJavadoc (),
  "io.circe" %% "circe-generic" % "0.13.0",
  "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
  "com.amazonaws" % "aws-lambda-java-core" % "1.2.1" withSources () withJavadoc (),
  "com.amazonaws" % "aws-java-sdk-s3" % "1.11.823" withSources () withJavadoc (),
  "com.amazonaws" % "aws-lambda-java-events" % "3.1.0" withSources () withJavadoc (),
  "org.scalatest" %% "scalatest" % "3.2.0" % Test,
  "com.codecommit" %% "cats-effect-testing-scalatest" % "0.4.0" % Test,
  "org.typelevel" %% "cats-effect-laws" % "2.1.3" % Test,
  "ch.qos.logback" % "logback-classic" % "1.2.3" % Runtime,
  "org.fusesource.jansi" % "jansi" % "1.18" % Runtime
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:postfixOps",
  "-language:higherKinds"
)

//assemblyMergeStrategy in assembly := (_ => MergeStrategy.first)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x                             => MergeStrategy.first
}
test in assembly := {} //no tests in assembly
