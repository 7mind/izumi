package com.github.pshirshov.izumi.distage.roles.launcher.test

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.roles.roles.{RoleDescriptor, RoleId, RoleService, RoleTask}
import com.github.pshirshov.izumi.logstage.api.IzLogger

case class TestServiceConf(
                            intval: Int
                            , strval: String
                            , overridenInt: Int
                            , systemPropInt: Int
                            , systemPropList: List[Int]
                          )

@RoleId(TestService.id)
class TestService(@ConfPath("testservice") val conf: TestServiceConf, logger: IzLogger) extends RoleService with RoleTask {
  override def start(): Unit = {
    logger.info("Test service started!")
  }
}

object TestService extends RoleDescriptor {
  override final val id = "testservice"
}
