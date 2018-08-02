package com.github.pshirshov.izumi.distage.roles.launcher

import com.github.pshirshov.izumi.distage.app
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.distage.roles.impl.{ScoptLauncherArgs, ScoptRoleApp}
import com.github.pshirshov.izumi.distage.roles.launcher.test.TestService
import com.github.pshirshov.izumi.distage.roles.roles.RoleService
import com.github.pshirshov.izumi.fundamentals.reflection.SourcePackageMaterializer._
import org.scalatest.WordSpec

class RoleAppTest extends WordSpec {

  "Role Launcher" should {
    "properly discover services to start" in {
      new RoleApp with ScoptRoleApp {

        override val pluginConfig: PluginLoaderDefaultImpl.PluginConfig = PluginConfig(
            debug = false
          , packagesEnabled = Seq(s"$thisPkg.test")
          , packagesDisabled = Seq.empty
        )

        override protected def start(context: Locator, bootstrapContext: app.BootstrapContext[ScoptLauncherArgs]): Unit = {
          val services = context.instances.map(_.value).collect({case t: RoleService => t}).toSet
          assert(services.size == 1)
          assert(services.exists(_.isInstanceOf[TestService]))
          super.start(context, bootstrapContext)
        }
      }.main(Array("testservice"))
    }
  }

}
