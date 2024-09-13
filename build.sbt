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
    name                      := "api",
    Test / parallelExecution  := false,
    Test / fork               := true,
    libraryDependencies ++= (httpServerDeps ++ awsLambdaDeps :+ lambdaRuntimeDeps),
    Compile / mainClass       := Some("com.amazonaws.services.lambda.runtime.api.client.AWSLambda"),
    buildInfoKeys             := Seq[BuildInfoKey](version),
    buildInfoPackage          := "dev.faustin0.info",
    topLevelDirectory         := None,
    graalVMNativeImageOptions := Seq(
      "--static",
      "--libc=musl",
      "--verbose",
      "--no-fallback",
      "-march=x86-64-v2",                                        // https://docs.aws.amazon.com/linux/al2023/ug/performance-optimizations.html
      "--strict-image-heap",
      "--report-unsupported-elements-at-runtime",
      "--initialize-at-build-time",
      "--initialize-at-run-time=scala.util.Random",
      "--initialize-at-run-time=org.http4s.multipart.Boundary$", // todo open PR on tapir?
      "--initialize-at-run-time=software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy",
      "--initialize-at-run-time=software.amazon.awssdk.services.dynamodb.DynamoDbRetryPolicy",
      "--enable-http",
      "--enable-https",
      "--enable-all-security-services",
      "--enable-url-protocols=https,http",
      "--enable-url-protocols=http",
//      "-H:+StaticExecutableWithDynamicLibC",                     // avoid http4s segmentation fault, an alternative to --libc=musl
      "-H:+ReportExceptionStackTraces",
      "-J-Dfile.encoding=UTF-8"
    ) ++ optimizationLevel(),
    excludeDependencies ++= Seq(
      // commons-logging is replaced by jcl-over-slf4j
      ExclusionRule(organization = "commons-logging", name = "commons-logging"),
      ExclusionRule(organization = "software.amazon.awssdk", name = "netty-nio-client"),
      ExclusionRule(organization = "software.amazon.awssdk", name = "apache-client")
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

def optimizationLevel(): Seq[String] =
  sys.env
    .get("OPTIMIZE_NATIVE")
    .collect {
      case "true"  => "-O2"
      case "false" => "-Ob"
    }
    .toSeq
