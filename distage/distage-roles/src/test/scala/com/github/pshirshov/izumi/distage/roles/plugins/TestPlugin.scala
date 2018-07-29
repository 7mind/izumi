package com.github.pshirshov.izumi.distage.roles.plugins

import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.roles.{RoleDescriptor, RoleId, TGTask, RoleAppService}
import com.github.pshirshov.izumi.logstage.api.IzLogger

@RoleId(TestService.id)
class TestService(logger: IzLogger) extends RoleAppService with TGTask {
  override def start(): Unit = {
    logger.info(s"Test service started!")
  }
}

object TestService extends RoleDescriptor {
  override final val id = "testservice"
}


class TestPlugin extends PluginDef {
  make[RoleAppService].named("testservice").from[TestService]
}
