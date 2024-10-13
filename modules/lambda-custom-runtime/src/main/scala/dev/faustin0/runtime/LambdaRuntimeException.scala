package dev.faustin0.runtime

sealed abstract private[runtime] class LambdaRuntimeException(msg: String) extends Exception(msg)

private[runtime] case object ContainerError extends LambdaRuntimeException(s"Container error. Non-recoverable state.")
