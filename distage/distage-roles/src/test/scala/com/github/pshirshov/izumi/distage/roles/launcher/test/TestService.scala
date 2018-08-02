package com.github.pshirshov.izumi.distage.roles.launcher.test

import com.github.pshirshov.izumi.distage.roles.roles.{RoleAppService, RoleAppTask, RoleDescriptor, RoleId}
import com.github.pshirshov.izumi.logstage.api.IzLogger

@RoleId(TestService.id)
class TestService(logger: IzLogger) extends RoleAppService with RoleAppTask {
  override def start(): Unit = {
    logger.info(s"Test service started!")
  }
}

object TestService extends RoleDescriptor {
  override final val id = "testservice"
}
