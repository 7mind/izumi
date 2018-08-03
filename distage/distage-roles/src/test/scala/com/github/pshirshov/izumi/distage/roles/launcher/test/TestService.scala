package com.github.pshirshov.izumi.distage.roles.launcher.test

import com.github.pshirshov.izumi.distage.roles.roles.{RoleService, RoleTask, RoleDescriptor, RoleId}
import com.github.pshirshov.izumi.logstage.api.IzLogger

@RoleId(TestService.id)
class TestService(logger: IzLogger) extends RoleService with RoleTask {
  override def start(): Unit = {
    logger.info("Test service started!")
  }
}

object TestService extends RoleDescriptor {
  override final val id = "testservice"
}
