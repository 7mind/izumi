package com.github.pshirshov.izumi.distage.roles.launcher

import java.io.File

import com.github.pshirshov.izumi.distage.roles.launcher.RoleLauncherArgs.WriteReference
import com.github.pshirshov.izumi.logstage.api.Log
import scopt.OptionParser

case class RoleArgs(name: String, configFile: Option[File])
