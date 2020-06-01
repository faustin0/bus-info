name := "bus-info"

version := "0.1"

scalaVersion := "2.13.2"

val http4sVersion = "0.21.4"

libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-effect" % "2.1.3" withSources() withJavadoc(),
  "org.http4s" %% "http4s-dsl" % http4sVersion withSources() withJavadoc(),
  "org.http4s" %% "http4s-blaze-server" % http4sVersion withSources() withJavadoc(),
  "org.http4s" %% "http4s-blaze-client" % http4sVersion withSources() withJavadoc(),
  "org.http4s" %% "http4s-scala-xml" % http4sVersion withSources() withJavadoc(),
  //  "org.slf4j" % "slf4j-simple" % "1.7.30" % Runtime,
  "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
  "org.scalatest" %% "scalatest" % "3.0.8" % Test,
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