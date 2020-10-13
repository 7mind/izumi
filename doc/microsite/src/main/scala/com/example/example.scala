package com.example

import distage.StandardAxis.Mode
import distage.config.ConfigModuleDef
import distage.plugins.PluginDef

final case class HostPort(host: String, port: Int)

final case class Config(hostPort: HostPort)

final class Service(val conf: Config, val otherService: OtherService)
final class OtherService

// OtherService class is not defined here, even though Service depends on it
final class AppPlugin extends PluginDef with ConfigModuleDef {
  tag(Mode.Prod)

  make[Service]
  makeConfig[Config]("config")
}
