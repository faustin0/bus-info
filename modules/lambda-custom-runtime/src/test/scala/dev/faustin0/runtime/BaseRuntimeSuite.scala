package dev.faustin0.runtime

import cats.effect._
import dev.faustin0.runtime.headers._
import io.circe._
import io.circe.syntax.EncoderOps
import munit.CatsEffectSuite
import org.http4s.Method.{ GET, POST }
import org.http4s.Uri.Path.Root
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.{ HttpRoutes, Uri, _ }

abstract class BaseRuntimeSuite extends CatsEffectSuite {
  val runtimeApi = Root / "testApi" / LambdaRuntimeAPIClient.ApiVersion / "runtime"

  implicit val jsonEncoder: EntityEncoder[IO, Json] =
    jsonEncoderWithPrinter[IO](Printer.noSpaces.copy(dropNullValues = true))

  def createTestEnv(
    funcName: IO[String] = IO("test"),
    memorySize: IO[Int] = IO(144),
    funcVersion: IO[String] = IO("1.0"),
    logGroupName: IO[String] = IO("test"),
    logStreamName: IO[String] = IO("test"),
    runtimeApi: IO[Uri] = IO(uri"testApi")
  ): LambdaRuntimeEnv[IO] = new LambdaRuntimeEnv[IO] {

    def lambdaFunctionName: IO[String] = funcName

    def lambdaFunctionMemorySize: IO[Int] = memorySize

    def lambdaFunctionVersion: IO[String] = funcVersion

    def lambdaLogGroupName: IO[String] = logGroupName

    def lambdaLogStreamName: IO[String] = logStreamName

    def lambdaRuntimeApi: IO[Uri] = runtimeApi
  }

  def defaultRoutes(invocationQuota: InvocationQuota): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> `runtimeApi` / "invocation" / "next"          =>
        for {
          _      <- invocationQuota.increment
          headers = Headers(
                      `Lambda-Runtime-Aws-Request-Id`("testId"),
                      `Lambda-Runtime-Deadline-Ms`(20),
                      `Lambda-Runtime-Invoked-Function-Arn`("test"),
                      `Lambda-Runtime-Trace-Id`("trace-id")
                    )
          body    = Json.obj("eventField" -> "test".asJson)
          resp   <- Ok(body, headers)
        } yield resp
      case POST -> `runtimeApi` / "invocation" / _ / "response" =>
        Ok()
      case POST -> `runtimeApi` / "invocation" / _ / "error"    =>
        Ok()
      case POST -> `runtimeApi` / "init" / "error"              =>
        Ok()
    }

  def testInvocationErrorRoute(eventualInvocationError: Deferred[IO, Json]): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case req @ POST -> `runtimeApi` / "invocation" / _ / "error" =>
      req.as[Json].flatTap(eventualInvocationError.complete) *> Accepted()
    }

  def testInvocationResponseRoute(eventualInvocationId: Deferred[IO, String]): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case POST -> `runtimeApi` / "invocation" / id / "response" =>
      eventualInvocationId.complete(id) >> Accepted()
    }

  def testInvocationResponseContainerErrorRoute(eventualInvocationId: Deferred[IO, String]): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case POST -> `runtimeApi` / "invocation" / id / "response" =>
      eventualInvocationId.complete(id) >> InternalServerError()
    }

  def testInitErrorRoute(eventualInitError: Deferred[IO, Json]): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case req @ POST -> `runtimeApi` / "init" / "error" =>
      req.as[Json].flatTap(eventualInitError.complete(_)) *> Accepted()
    }

  def testInitContainerErrorRoute(eventualInitError: Deferred[IO, Json]): HttpRoutes[IO] =
    HttpRoutes.of[IO] { case req @ POST -> `runtimeApi` / "init" / "error" =>
      req.as[Json].flatTap(eventualInitError.complete(_)) *> InternalServerError()
    }

  def expectedErrorBody(errorMessage: String = "Error", errorType: String = "Exception"): Json =
    Json.obj(
      "errorMessage" -> errorMessage.asJson,
      "errorType"    -> errorType.asJson,
      "stackTrace"   -> List[String]().asJson
    )

  def lambdaErrorBodyJsonNoStackTrace(errorBody: Json): Option[Json] =
    errorBody.hcursor.downField("stackTrace").withFocus(_ => List[String]().asJson).top

}
