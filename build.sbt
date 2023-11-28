import Dependencies._
import sbt.Keys.parallelExecution

Global / onChangedBuildSource := ReloadOnSourceChanges

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
  scalaVersion := "2.13.12",
  scalacOptions ++= Seq("-encoding", "utf8"),
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
  .enablePlugins(BuildInfoPlugin, GraalVMNativeImagePlugin)
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name                           := "api",
    Test / parallelExecution       := false,
    Test / fork                    := true,
    libraryDependencies ++= (httpServerDeps ++ Seq(
      "org.typelevel" %% "feral-lambda-http4s"                      % "0.2.4",
      "com.amazonaws"  % "aws-lambda-java-runtime-interface-client" % "2.4.1",
      "com.amazonaws"  % "aws-lambda-java-core"                     % "1.2.3",
      "com.amazonaws"  % "aws-lambda-java-events"                   % "3.11.3"
    )),
    Compile / mainClass            := Some("com.amazonaws.services.lambda.runtime.api.client.AWSLambda"),
    buildInfoKeys                  := Seq[BuildInfoKey](version),
    buildInfoPackage               := "dev.faustin0.info",
    topLevelDirectory              := None,
//    nativeImageOptions ++= Seq(
//      "--no-fallback",
//      "--link-at-build-time",
//      "--initialize-at-build-time=org.slf4j",
//      "--enable-url-protocols=http",
//      "--add-opens java.base/java.util=ALL-UNNAMED",
//      "--enable-url-protocols=https,http",
//      s"-H:ReflectionConfigurationFiles=${target.value / "native-image-configs" / "reflect-config.json"}",
//      s"-H:ConfigurationFileDirectories=${target.value / "native-image-configs"}",
//      "-H:+JNI"
//      s"-H:ReflectionConfigurationFiles=${target.value / "native-image-configs" / "reflect-config.json"}",
//      s"-H:ConfigurationFileDirectories=${target.value / "native-image-configs"}"
//    ),
//    nativeImageVersion       := "22.1.0", // It should be at least version 21.0.0
    graalVMNativeImageGraalVersion := Some("22.3.3"),
    graalVMNativeImageOptions      := Seq(
      "--static",
      "--verbose",
      "--no-fallback",
      "--report-unsupported-elements-at-runtime",
      "--initialize-at-build-time=org.slf4j",
      "--initialize-at-build-time=org.slf4j.LoggerFactory",
      "--initialize-at-build-time=ch.qos.logback",
      "--initialize-at-build-time=org.apache.logging.slf4j", // non serve rorse
      "--enable-http",
      "--enable-https",
      "--enable-all-security-services",
      "--enable-url-protocols=https,http",
      "--enable-url-protocols=http",
//      "--allow-incomplete-classpath",
//      "--libc=musl",     // questo serve per http4s e segmentanio fault
      "-H:+StaticExecutableWithDynamicLibC",                 // questo server per http4s e segmentanio fault
      "-H:+ReportExceptionStackTraces",
      "-J-Dfile.encoding=UTF-8"
//      "-H:+AllowIncompleteClasspath",
    ),
    excludeDependencies ++= Seq(
      // commons-logging is replaced by jcl-over-slf4j
      ExclusionRule("commons-logging", "commons-logging"),
      ExclusionRule("io.netty")
    )
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
