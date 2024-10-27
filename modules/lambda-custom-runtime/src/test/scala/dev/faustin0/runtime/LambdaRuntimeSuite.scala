package dev.faustin0.runtime

import cats.effect._
import cats.syntax.all._
import dev.faustin0.runtime.models.{ Context, Invocation }
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.client.Client
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.{ util => ju }
import scala.concurrent.duration.{ DurationInt, FiniteDuration }

class LambdaRuntimeSuite extends BaseRuntimeSuite {
  implicit private val logger: SelfAwareStructuredLogger[IO] = Slf4jLogger.getLogger[IO]

  private val handlerTimeout: FiniteDuration = 5.seconds

  test("The runtime can process an event and pass the result to the invocation response url") {
    implicit val env: LambdaRuntimeEnv[IO] = createTestEnv()
    for {
      invocationQuota      <- InvocationQuota.make(1)
      eventualInvocationId <- Deferred[IO, String]
      client                = Client.fromHttpApp[IO](
                                (testInvocationResponseRoute(eventualInvocationId) <+> defaultRoutes(invocationQuota)).orNotFound
                              )
      handler               = (inv: Invocation[IO, Json]) => IO.println("Invocation!!").as(inv.event.some)
      _                    <- IO.race(LambdaRuntime[IO, Json, Json](client)(Resource.pure(handler)), eventualInvocationId.get)
                                .map {
                                  case Left(_)             => fail("Runtime ended")
                                  case Right(invocationId) => assertEquals(invocationId, "testId")
                                }
                                .timeout(handlerTimeout)
      _                    <- invocationQuota.ensureIsReached
    } yield ()
  }

  test("A valid context and JSON event is passed to the handler function during invocation") {
    implicit val env: LambdaRuntimeEnv[IO] = createTestEnv()
    for {
      invocationQuota     <- InvocationQuota.make(1)
      eventualInvocation  <- Deferred[IO, (Json, Context[IO])]
      client               = Client.fromHttpApp[IO](defaultRoutes(invocationQuota).orNotFound)
      handler              =
        (inv: Invocation[IO, Json]) => eventualInvocation.complete((inv.event, inv.context)) >> none[Json].pure[IO]
      runtimeFiber        <- LambdaRuntime(client)(Resource.eval(handler.pure[IO])).start
      invocation          <- eventualInvocation.get.timeout(handlerTimeout).guarantee(runtimeFiber.cancel)
      (jsonEvent, context) = invocation
      expectedJson         = Json.obj("eventField" -> "test".asJson)
      _                   <- invocationQuota.ensureIsReached
    } yield {
      assert(clue(jsonEvent) eqv clue(expectedJson))
      //      assert(clue(context.clientContext).exists(_.client.appTitle == "test"))
      assertEquals(context.functionName, "test")
      assertEquals(context.memoryLimitInMB, 144)
    }
  }

  test(
    "The runtime will call the initialization error url and raise an exception when the handler function cannot be acquired"
  ) {
    implicit val env: LambdaRuntimeEnv[IO] = createTestEnv()
    for {
      invocationQuota         <- InvocationQuota.make(0)
      eventualInitError       <- Deferred[IO, Json]
      client                   = Client.fromHttpApp((testInitErrorRoute(eventualInitError) <+> defaultRoutes(invocationQuota)).orNotFound)
      badHandlerResource       = Resource.make[IO, Invocation[IO, Json] => IO[Option[Json]]](
                                   IO.raiseError(new Exception("Failure acquiring handler"))
                                 )(_ => IO.unit)
      runtimeFiber            <- LambdaRuntime(client)(badHandlerResource).start
      errorRequest            <- eventualInitError.get.timeout(handlerTimeout)
      errorRequestNoStackTrace = lambdaErrorBodyJsonNoStackTrace(errorRequest)
      runtimeOutcome          <- runtimeFiber.join.timeout(handlerTimeout)
      _                       <- invocationQuota.ensureIsReached
    } yield {
      assert(errorRequestNoStackTrace.exists(_ eqv expectedErrorBody("Failure acquiring handler")))
      assert(runtimeOutcome.isError)
    }
  }

  test("The runtime will call the invocation error url when the handler function errors during processing") {
    implicit val env: LambdaRuntimeEnv[IO] = createTestEnv()
    for {
      invocationQuota         <- InvocationQuota.make(1)
      eventualInvocationError <- Deferred[IO, Json]
      client                   = Client.fromHttpApp(
                                   (testInvocationErrorRoute(eventualInvocationError) <+> defaultRoutes(invocationQuota)).orNotFound
                                 )
      handler                  = (_: Invocation[IO, Json]) => IO.raiseError[Option[Json]](new Exception("Error"))
      runtimeFiber            <- LambdaRuntime(client)(Resource.eval(handler.pure[IO])).start
      errorRequest            <- eventualInvocationError.get.timeout(handlerTimeout)
      errorRequestNoStackTrace = lambdaErrorBodyJsonNoStackTrace(errorRequest)
      _                       <- runtimeFiber.cancel
      outcome                 <- runtimeFiber.join
      _                       <- invocationQuota.ensureIsReached
    } yield {
      assert(errorRequestNoStackTrace.exists(_ eqv expectedErrorBody()))
      assertEquals(outcome, Outcome.Canceled[IO, Throwable, Unit]())
    }
  }

  test("The runtime will call the initialization error url when a needed environment variable is not available") {
    val lambdaFunctionNameEnvVar           = LambdaRuntimeEnv.AWS_LAMBDA_FUNCTION_NAME
    implicit val env: LambdaRuntimeEnv[IO] =
      createTestEnv(funcName = IO.raiseError(new NoSuchElementException(lambdaFunctionNameEnvVar)))
    for {
      invocationQuota         <- InvocationQuota.make(0)
      eventualInitError       <- Deferred[IO, Json]
      client                   = Client.fromHttpApp((testInitErrorRoute(eventualInitError) <+> defaultRoutes(invocationQuota)).orNotFound)
      handler                  = (_: Invocation[IO, Json]) => Json.obj().some.pure[IO]
      runtimeFiber            <- LambdaRuntime(client)(Resource.eval(handler.pure[IO])).start
      errorRequest            <- eventualInitError.get.timeout(handlerTimeout)
      errorRequestNoStackTrace = lambdaErrorBodyJsonNoStackTrace(errorRequest)
      runtimeOutcome          <- runtimeFiber.join.timeout(handlerTimeout)
      _                       <- invocationQuota.ensureIsReached
    } yield {
      assert(
        errorRequestNoStackTrace.exists(_ eqv expectedErrorBody(lambdaFunctionNameEnvVar, "NoSuchElementException"))
      )
      val Outcome.Errored(ex: ju.NoSuchElementException) = runtimeOutcome: @unchecked
      assertEquals(ex.getMessage(), "AWS_LAMBDA_FUNCTION_NAME")
    }
  }

  test("The runtime will crash when init error api returns the container error") {
    val lambdaFunctionNameEnvVar           = LambdaRuntimeEnv.AWS_LAMBDA_FUNCTION_NAME
    implicit val env: LambdaRuntimeEnv[IO] =
      createTestEnv(funcName = IO.raiseError(new NoSuchElementException(lambdaFunctionNameEnvVar)))
    for {
      invocationQuota         <- InvocationQuota.make(0)
      eventualInitError       <- Deferred[IO, Json]
      client                   = Client.fromHttpApp(
                                   (testInitContainerErrorRoute(eventualInitError) <+> defaultRoutes(invocationQuota)).orNotFound
                                 )
      handler                  = (_: Invocation[IO, Json]) => Json.obj().some.pure[IO]
      runtimeFiber            <- LambdaRuntime(client)(Resource.eval(handler.pure[IO])).start
      errorRequest            <- eventualInitError.get.timeout(handlerTimeout)
      errorRequestNoStackTrace = lambdaErrorBodyJsonNoStackTrace(errorRequest)
      runtimeOutcome          <- runtimeFiber.join.timeout(handlerTimeout)
      _                       <- invocationQuota.ensureIsReached
    } yield {
      assert(
        errorRequestNoStackTrace.exists(_ eqv expectedErrorBody(lambdaFunctionNameEnvVar, "NoSuchElementException"))
      )
      assertEquals(runtimeOutcome, Outcome.Errored[IO, Throwable, Unit](ContainerError))
    }

  }

  test("The runtime will recover from a function error and continue processing next invocation") {
    implicit val env: LambdaRuntimeEnv[IO] = createTestEnv()
    for {
      invocationQuota            <- InvocationQuota.make(2)
      eventualInvocationError    <- Deferred[IO, Json]
      eventualResponse           <- Deferred[IO, String]
      call                       <- Ref[IO].of(0)
      client                      = Client.fromHttpApp(
                                      (testInvocationErrorRoute(eventualInvocationError)
                                        <+> testInvocationResponseRoute(eventualResponse)
                                        <+> defaultRoutes(invocationQuota)).orNotFound
                                    )
      handler                     = (_: Invocation[IO, Json]) =>
                                      for {
                                        call <- call.getAndUpdate(_ + 1)
                                        resp <-
                                          if (call === 0) IO.raiseError(new Exception("First invocation error"))
                                          else Json.obj().some.pure[IO]
                                      } yield resp
      runtimeFiber               <- LambdaRuntime(client)(Resource.eval(handler.pure[IO])).start
      invocationError            <- eventualInvocationError.get.timeout(handlerTimeout)
      invocationErrorNoStackTrace = lambdaErrorBodyJsonNoStackTrace(invocationError)
      secondInvocationResponse   <- eventualResponse.get.timeout(handlerTimeout)
      _                          <- runtimeFiber.cancel
      _                          <- invocationQuota.ensureIsReached
    } yield {
      assert(invocationErrorNoStackTrace.exists(_ eqv expectedErrorBody("First invocation error")))
      assertEquals(secondInvocationResponse, "testId")
    }
  }

  test("The runtime will crash when response api returns the container error") {
    implicit val env: LambdaRuntimeEnv[IO] = createTestEnv()
    for {
      invocationQuota      <- InvocationQuota.make(1)
      eventualInvocationId <- Deferred[IO, String]
      client                = Client.fromHttpApp(
                                (testInvocationResponseContainerErrorRoute(eventualInvocationId) <+> defaultRoutes(
                                  invocationQuota
                                )).orNotFound
                              )
      handler               = (_: Invocation[IO, Json]) => Json.obj().some.pure[IO]
      runtimeFiber         <- LambdaRuntime(client)(Resource.eval(handler.pure[IO])).start
      errorRequest         <- eventualInvocationId.get.timeout(handlerTimeout)
      runtimeOutcome       <- runtimeFiber.join.timeout(handlerTimeout)
      _                    <- invocationQuota.ensureIsReached
    } yield {
      assertEquals(errorRequest, "testId")
      assertEquals(runtimeOutcome, Outcome.Errored[IO, Throwable, Unit](ContainerError))
    }
  }

}
