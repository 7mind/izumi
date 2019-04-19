package com.github.pshirshov.izumi.distage.roles.launcher

import java.io.File

@deprecated("Migrate to new role infra", "2019-04-19")
case class RoleArgs(name: String, configFile: Option[File])
