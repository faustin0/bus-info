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
  scalaVersion := "2.13.8",
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

lazy val assemblySetting = assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "maven", "org.webjars", "swagger-ui", "pom.properties")             =>
    MergeStrategy.singleOrError
  case PathList("META-INF", "io.netty.versions.properties")                                     =>
    MergeStrategy.last
  case "module-info.class"                                                                      =>
    MergeStrategy.concat
  case "mime.types"                                                                             =>
    MergeStrategy.filterDistinctLines
  case PathList("software", "amazon", "awssdk", "global", "handlers", "execution.interceptors") =>
    MergeStrategy.filterDistinctLines
  case s                                                                                        =>
    MergeStrategy.defaultMergeStrategy(s)
}

lazy val root = (project in file("."))
  .aggregate(core, api, importer, tests)
  .settings(name := "bus-info")
  .settings(
    update / aggregate := false
  )

lazy val core = project
  .in(file("modules/core"))
  .settings(commonSettings)
  .settings(name := "core")
  .settings(Test / parallelExecution := false)
  .settings(Test / fork := true)
  .settings(assemblySetting)
  .settings(assembly / test := {})
  .settings(libraryDependencies ++= httpClientDeps ++ dynamoDeps)
//  .settings(assemblyJarName in assembly := "bus-info-app.jar")

lazy val api = project
  .in(file("modules/api"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(core)
  .settings(commonSettings)
  .settings(name := "api")
  .settings(Test / parallelExecution := false)
  .settings(Test / fork := true)
  .settings(assemblySetting)
  .settings(assembly / test := {})
  .settings(libraryDependencies ++= httpServerDeps)
  .settings(assembly / assemblyJarName := "bus-info-app.jar")
  .settings(
    buildInfoKeys    := Seq[BuildInfoKey](version),
    buildInfoPackage := "dev.faustin0.info"
  )

lazy val importer = project
  .in(file("modules/importer"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(name := "importer")
  .settings(Test / parallelExecution := false)
  .settings(Test / fork := true)
  .settings(assemblySetting)
  .settings(assembly / test := {})
  .settings(libraryDependencies ++= awsDeps)
  .settings(assembly / assemblyJarName := "bus-stops-importer.jar")

lazy val tests = project
  .in(file("modules/tests"))
  .dependsOn(core, importer, api)
  .configs(IntegrationTest)
  .settings(commonSettings)
  .settings(Defaults.itSettings)
  .settings(name := "tests")
  .settings(Test / parallelExecution := false)
  .settings(Test / fork := true)
  .settings(IntegrationTest / fork := true)
  .settings(libraryDependencies ++= awsDeps)
  .disablePlugins(sbtassembly.AssemblyPlugin)
