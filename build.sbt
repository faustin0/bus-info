import Dependencies._
import sbt.Keys.parallelExecution

inThisBuild(
  List(
    organization         := "dev.faustin0",
    developers           := List(
      Developer("faustin0", "Fausto Di Natale", "", url("https://github.com/faustin0")),
      Developer("azanin", "Alessandro Zanin", "ale.zanin90@gmail.com", url("https://github.com/azanin"))
    ),
    homepage             := Some(url("https://github.com/faustin0/bus-info")),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    pomIncludeRepository := { _ => false },
    version              := "0.2.2"
  )
)

// General Settings
lazy val commonSettings = Seq(
  scalaVersion := "2.13.9",
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % kindProjectorV cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % betterMonadicForV),
  libraryDependencies ++= dependencies ++ testDependencies
)

lazy val root = (project in file("."))
  .aggregate(core, api, importer, tests)
  .settings(name := "bus-info")
  .settings(
    update / aggregate := false
  )

lazy val core = project
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(
    name                     := "core",
    Test / parallelExecution := false,
    Test / fork              := true,
    libraryDependencies ++= httpClientDeps ++ dynamoDeps
  )

lazy val api = project
  .in(file("modules/api"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, LauncherJarPlugin)
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name                     := "api",
    Test / parallelExecution := false,
    Test / fork              := true,
    libraryDependencies ++= httpServerDeps,
    Compile / mainClass      := Some("dev.faustin0.api.BusInfoApp"),
    buildInfoKeys            := Seq[BuildInfoKey](version),
    buildInfoPackage         := "dev.faustin0.info",
    topLevelDirectory        := None
  )

lazy val importer = project
  .in(file("modules/importer"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging)
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name                     := "importer",
    Test / parallelExecution := false,
    Test / fork              := true,
    libraryDependencies ++= awsDeps,
    topLevelDirectory        := None
  )

lazy val tests = project
  .in(file("modules/tests"))
  .dependsOn(core, importer, api)
  .configs(IntegrationTest)
  .settings(commonSettings)
  .settings(Defaults.itSettings)
  .settings(
    name                     := "tests",
    Test / parallelExecution := false,
    Test / fork              := true,
    IntegrationTest / fork   := true,
    libraryDependencies ++= awsDeps
  )
