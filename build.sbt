import Dependencies.{betterMonadicForV, dependencies, kindProjectorV, testDependencies}

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
