package dev.faustin0.runtime.models

import scala.concurrent.duration.FiniteDuration

final case class Context[F[_]](
  functionName: String,
  functionVersion: String,
  invokedFunctionArn: String,
  memoryLimitInMB: Int,
  awsRequestId: String,
  logGroupName: String,
  logStreamName: String,
  identity: Option[CognitoIdentity],
  clientContext: Option[ClientContext],
  remainingTime: F[FiniteDuration],
  xRayTraceId: Option[String]
)
