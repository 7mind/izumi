package com.github.pshirshov.izumi.distage.roles.launcher

import java.nio.file.Paths

import com.github.pshirshov.izumi.distage.app
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.roles.plugins.TestService
import com.github.pshirshov.izumi.distage.roles.roles.RoleAppService
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpec

//object TGAppManualTestWrapper {
//  def main(args: Array[String]): Unit = {
//    Quirks.discard(args)
//    new TGApp().main(Array("role", "-i", "testservice"))
//  }
//}

class RoleAppTest extends WordSpec {
  "TG Launcher" should {
    "properly discover services to start" in {
      new RoleApp {
        override protected def start(context: Locator, bootstrapContext: app.BootstrapContext[RoleLauncherArgs]): Unit = {
          val services = context.instances.map(_.value).collect({case t: RoleAppService => t}).toSet
          assert(services.size == 1)
          assert(services.exists(_.isInstanceOf[TestService]))
          super.start(context, bootstrapContext)
        }
      }.main(Array("testservice"))
    }

    "properly discover services to start/2" in {
      new RoleApp {
        override protected def start(context: Locator, bootstrapContext: app.BootstrapContext[RoleLauncherArgs]): Unit = {
          val services = context.instances.map(_.value).collect({case t: RoleAppService => t}).toSet
          assert(services.size == 2)
          assert(services.exists(_.isInstanceOf[TestService]))
          super.start(context, bootstrapContext)
        }
      }.main(Array("testservice", "configwriter"))
    }

    "provide config writer service" in {
      System.setProperty("intval", "265")
      ConfigFactory.invalidateCaches()

      new RoleApp {
        override protected def start(context: Locator, bootstrapContext: app.BootstrapContext[RoleLauncherArgs]): Unit = {
          val services = context.instances.map(_.value).collect({case t: RoleAppService => t}).toSet
          assert(services.size == 1)
          assert(services.exists(_.isInstanceOf[ConfigWriter]))
          super.start(context, bootstrapContext)
        }
      }.main(Array("-wr", "-d", "target/config"))

      // TODO: ugly copypaste
      val f = Paths.get("target", "config", "testservice-0.0.0-UNKNOWN.conf").toFile
      assert(f.exists())
      ConfigFactory.defaultApplication()
      val parsed = ConfigFactory.parseFile(f)
      assert(parsed.getValue("intval").unwrapped() == "265")
    }
  }
}
