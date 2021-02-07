import Dependencies._

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
  scalaVersion := "2.13.5",
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

lazy val assemblySetting = assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties") => MergeStrategy.singleOrError
  case PathList("META-INF", "io.netty.versions.properties")                         => MergeStrategy.last
  case "module-info.class"                                                          => MergeStrategy.concat
  case "mime.types"                                                                 => MergeStrategy.filterDistinctLines
  case s                                                                            => MergeStrategy.defaultMergeStrategy(s)
}

lazy val root = (project in file("."))
  .aggregate(core, api, importer)
  .settings(name := "bus-info")
  .settings(
    update / aggregate := false
  )

lazy val core = project
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(name := "core")
  .settings(parallelExecution in Test := false)
  .settings(fork in Test := true)
  .settings(assemblySetting)
  .settings(test in assembly := {})
  .settings(libraryDependencies ++= httpClientDeps ++ dynamoDeps)
//  .settings(assemblyJarName in assembly := "bus-info-app.jar")

lazy val api = project
  .in(file("modules/api"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(name := "api")
  .settings(parallelExecution in Test := false)
  .settings(fork in Test := true)
  .settings(assemblySetting)
  .settings(test in assembly := {})
  .settings(libraryDependencies ++= httpServerDeps)
  .settings(assemblyJarName in assembly := "bus-info-app.jar")

lazy val importer = project
  .in(file("modules/importer"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(name := "importer")
  .settings(parallelExecution in Test := false)
  .settings(fork in Test := true)
  .settings(assemblySetting)
  .settings(test in assembly := {})
  .settings(libraryDependencies ++= awsDeps)
  .settings(assemblyJarName in assembly := "bus-stops-importer.jar")
