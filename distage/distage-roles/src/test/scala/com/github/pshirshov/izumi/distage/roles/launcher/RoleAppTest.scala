package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.app
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.distage.roles.roles.{RoleAppService, RoleAppTask, RoleDescriptor, RoleId}
import com.github.pshirshov.izumi.logstage.api.IzLogger
import org.scalatest.WordSpec

@RoleId(TestService.id)
class TestService(logger: IzLogger) extends RoleAppService with RoleAppTask {
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

class RoleAppTest extends WordSpec {

  "Role Launcher" should {
    "properly discover services to start" in {
      new RoleApp {
        override val pluginConfig: PluginLoaderDefaultImpl.PluginConfig = PluginConfig(
            debug = false
          , packagesEnabled = Seq("com.github.pshirshov.izumi.distage.roles.launcher")
          , packagesDisabled = Seq.empty
        )

        override protected def start(context: Locator, bootstrapContext: app.BootstrapContext[RoleLauncherArgs]): Unit = {
          val services = context.instances.map(_.value).collect({case t: RoleAppService => t}).toSet
          assert(services.size == 1)
          assert(services.exists(_.isInstanceOf[TestService]))
          super.start(context, bootstrapContext)
        }
      }.main(Array("testservice"))
    }
  }

}
