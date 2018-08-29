package com.github.pshirshov.izumi.distage.roles.launcher.test

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.roles.roles.{RoleDescriptor, RoleId, RoleService, RoleTask}
import com.github.pshirshov.izumi.logstage.api.IzLogger

trait Dummy


case class TestServiceConf(
                            intval: Int
                            , strval: String
                            , overridenInt: Int
                            , systemPropInt: Int
                            , systemPropList: List[Int]
                          )

@RoleId(TestService.id)
class TestService(
                   @ConfPath("testservice") val conf: TestServiceConf
                   , val dummies: Set[Dummy]
                   , logger: IzLogger
                 ) extends RoleService with RoleTask {
  override def start(): Unit = {
    logger.info(s"Test service started, dummies: $dummies")
  }

  override def stop(): Unit = {
    logger.info(s"Test service is going to stop")
  }
}

object TestService extends RoleDescriptor {
  override final val id = "testservice"
}
