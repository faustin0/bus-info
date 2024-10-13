package dev.faustin0.runtime.models

import io.circe.JsonObject

final case class ClientContext(
  client: ClientContextClient,
  env: ClientContextEnv,
  custom: JsonObject
)

final case class ClientContextClient(
  installationId: String,
  appTitle: String,
  appVersionName: String,
  appVersionCode: String,
  appPackageName: String
)

final case class ClientContextEnv(
  platformVersion: String,
  platform: String,
  make: String,
  model: String,
  locale: String
)
