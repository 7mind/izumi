package com.github.pshirshov.izumi.distage.roles.launcher

import java.nio.file.Paths

import com.github.pshirshov.izumi.distage.app.AppFailureHandler
import com.github.pshirshov.izumi.distage.model.Locator
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.distage.roles.impl.ScoptRoleApp
import com.github.pshirshov.izumi.distage.roles.launcher.test._
import com.github.pshirshov.izumi.distage.roles.roles.RoleService
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks._
import com.github.pshirshov.izumi.fundamentals.platform.resources.ArtifactVersion
import com.github.pshirshov.izumi.fundamentals.reflection.SourcePackageMaterializer._
import com.typesafe.config.ConfigFactory
import org.scalatest.WordSpec


class RoleAppTest extends WordSpec {

  def withProperties(properties: (String, String)*)(f: => Unit): Unit = {
    try {
      properties.foreach {
        case (k, v) =>
          System.setProperty(k, v)
      }
      ConfigFactory.invalidateCaches()
      f
    } finally {
      properties.foreach {
        case (k, _) =>
        System.clearProperty(k)
      }
      ConfigFactory.invalidateCaches()
    }
  }


  "Role Launcher" should {
    "properly discover services to start" in withProperties(overrides.toSeq :_*) {
      new RoleApp with ScoptRoleApp {
        override def handler: AppFailureHandler = AppFailureHandler.NullHandler

        override final val using = Seq.empty

        override val pluginConfig: PluginLoaderDefaultImpl.PluginConfig = PluginConfig(
          debug = false
          , packagesEnabled = Seq(s"$thisPkg.test")
          , packagesDisabled = Seq.empty
        )


        override protected def start(context: Locator): Unit = {
          super.start(context)

          val services = context.instances.map(_.value).collect({ case t: RoleService => t }).toSet
          assert(services.size == 2)
          assert(services.exists(_.isInstanceOf[TestService]))

          val service = services.collect({case t: TestService => t}).head
          val conf = service.conf
          assert(conf.intval == 123)
          assert(conf.strval == "xxx")
          assert(conf.overridenInt == 111)
          assert(conf.systemPropInt == 265)
          assert(conf.systemPropList == List(111, 222))
          assert(service.dummies.isEmpty)
          assert(service.closeables.size == 5)

          val closeablesInDepOrder = Seq(context.get[Resource5], context.get[Resource2], context.get[Resource1])
          val componentsInDepOrder = Seq(context.get[Resource6], context.get[Resource4], context.get[Resource3])
          val integrationsInDepOrder = Seq(context.get[Resource2], context.get[Resource1])

          assert(service.counter.startedCloseables == closeablesInDepOrder)
          assert(service.counter.startedRoleComponents == componentsInDepOrder)
          assert(service.counter.closedRoleComponents == componentsInDepOrder.reverse)
          assert(service.counter.closedCloseables == closeablesInDepOrder.reverse)
          assert(service.counter.checkedResources == integrationsInDepOrder)

          verifyConfig(context)
          ()
        }
      }.main(Array("-wr", "-d", "target/config-dump", "testservice", "configwriter"))
    }

    "support config minimization" in withProperties(overrides.toSeq :_*) {
      new RoleApp with ScoptRoleApp {
        override def handler: AppFailureHandler = AppFailureHandler.NullHandler

        override final val using = Seq.empty

        override val pluginConfig: PluginLoaderDefaultImpl.PluginConfig = PluginConfig(
          debug = false
          , packagesEnabled = Seq(s"$thisPkg.test")
          , packagesDisabled = Seq.empty
        )

        override protected def start(context: Locator): Unit = {
          super.start(context)
          verifyConfig(context)
          ()
        }
      }.main(Array("-wr", "-d", "target/config-dump", "configwriter"))
    }
  }

  private def verifyConfig(context: Locator) = {
    val version = context.get[ArtifactVersion]("launcher-version")
    val justConfig = Paths.get("target", "config-dump", s"testservice-${version.version}.conf").toFile
    val minConfig = Paths.get("target", "config-dump", s"testservice-minimized-${version.version}.conf").toFile

    try {
      assert(justConfig.exists())
      assert(minConfig.exists())
    } finally {
      justConfig.delete().discard()
      minConfig.delete().discard()
    }
  }

  private val overrides = Map(
    "testservice.systemPropInt" -> "265"
    , "testservice.systemPropList.0" -> "111"
    , "testservice.systemPropList.1" -> "222"
  )

}
