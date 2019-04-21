package com.github.pshirshov.izumi.distage.roles

import cats.effect._
import com.github.pshirshov.izumi.distage.app.BootstrapConfig
import com.github.pshirshov.izumi.distage.plugins.load.PluginLoaderDefaultImpl.PluginConfig
import com.github.pshirshov.izumi.fundamentals.platform.cli.{Parameters, RoleArg}
import com.github.pshirshov.izumi.fundamentals.reflection.SourcePackageMaterializer._


object TestLauncher extends RoleAppLauncher.LauncherF[IO]() {
  protected val bootstrapConfig: BootstrapConfig = BootstrapConfig(
    PluginConfig(
      debug = false
      , packagesEnabled = Seq(s"$thisPkg.test")
      , packagesDisabled = Seq.empty
    )
  )
}

object Run extends RoleAppMain.Default(TestLauncher) {
  override protected def requiredRoles: Vector[RoleArg] = Vector(
    RoleArg("testrole00", Parameters.empty, Vector.empty),
    RoleArg("testrole01", Parameters.empty, Vector.empty),
    RoleArg("testrole02", Parameters.empty, Vector.empty),
    RoleArg("testtask00", Parameters.empty, Vector.empty),
  )
}

//class RoleAppTest extends WordSpec {
//
//  def withProperties(properties: (String, String)*)(f: => Unit): Unit = {
//    try {
//      properties.foreach {
//        case (k, v) =>
//          System.setProperty(k, v)
//      }
//      ConfigFactory.invalidateCaches()
//      f
//    } finally {
//      properties.foreach {
//        case (k, _) =>
//          System.clearProperty(k)
//      }
//      ConfigFactory.invalidateCaches()
//    }
//  }
//
//  "Role Launcher" should {
//    "properly discover services to start" in withProperties(overrides.toSeq: _*) {
//      new ScoptRoleApp[IzumiScoptLauncherArgs] {
//
//        override protected def tagDisablingStrategy(params: IzumiScoptLauncherArgs): BindingTag.Expressions.Composite = {
//          this.filterProductionTags(params.dummyStorage)
//        }
//
//        override protected def setupContext(params: IzumiScoptLauncherArgs, args: StrategyArgs): ApplicationBootstrapStrategy = {
//          super.setupContext(params, args)
//        }
//
//        override type CommandlineConfig = IzumiScoptLauncherArgs
//
//        override def handler: AppFailureHandler = AppFailureHandler.NullHandler
//
//        override final val using = Seq.empty
//
//        override val pluginConfig: PluginLoaderDefaultImpl.PluginConfig = PluginConfig(
//          debug = false
//          , packagesEnabled = Seq(s"$thisPkg.test")
//          , packagesDisabled = Seq.empty
//        )
//
//
//        override protected def start(context: Locator): Unit = {
//          super.start(context)
//
//          val services = context.instances.map(_.value).collect({ case t: RoleService => t }).toSet
//          assert(services.size == 2)
//          assert(services.exists(_.isInstanceOf[TestService]))
//
//          val service = services.collect({ case t: TestService => t }).head
//          val conf = service.conf
//          assert(conf.intval == 123)
//          assert(conf.strval == "xxx")
//          assert(conf.overridenInt == 111)
//          assert(conf.systemPropInt == 265)
//          assert(conf.systemPropList == List(111, 222))
//          assert(service.dummies.isEmpty)
//          assert(service.closeables.size == 5)
//
//          val closeablesInDepOrder = Seq(context.get[Resource5], context.get[Resource2], context.get[Resource1])
//          val componentsInDepOrder = Seq(context.get[Resource6], context.get[Resource4], context.get[Resource3])
//          val integrationsInDepOrder = Seq(context.get[Resource2], context.get[Resource1])
//
//          assert(service.counter.startedCloseables == closeablesInDepOrder)
//          assert(service.counter.startedRoleComponents == componentsInDepOrder)
//          assert(service.counter.closedRoleComponents == componentsInDepOrder.reverse)
//          assert(service.counter.closedCloseables == closeablesInDepOrder.reverse)
//          assert(service.counter.checkedResources == integrationsInDepOrder)
//
//          verifyConfig(context)
//          ()
//        }
//      }.main(Array("-wr", "-d", "target/config-dump", "testservice", "configwriter"))
//    }
//
//    "support config minimization" in withProperties(overrides.toSeq: _*) {
//      new ScoptRoleApp[IzumiScoptLauncherArgs] {
//
//        override def handler: AppFailureHandler = AppFailureHandler.NullHandler
//
//        override final val using = Seq.empty
//
//        override val pluginConfig: PluginLoaderDefaultImpl.PluginConfig = PluginConfig(
//          debug = false
//          , packagesEnabled = Seq(s"$thisPkg.test")
//          , packagesDisabled = Seq.empty
//        )
//
//        override protected def start(context: Locator): Unit = {
//          super.start(context)
//          verifyConfig(context)
//          ()
//        }
//      }.main(Array("-wr", "-d", "target/config-dump", "configwriter"))
//    }
//
//    "support external option parsers in scalaopt" in withProperties(overrides.toSeq: _*) {
//
//      val testRole = RoleArgs.apply("CUSTOM", None)
//
//      new ScoptRoleApp[IzumiScoptLauncherArgs] {
//        override def handler: AppFailureHandler = AppFailureHandler.NullHandler
//
//        override final val using = Seq.empty
//
//        override protected val externalParsers: Set[ScoptLauncherArgs.ParserExtenstion[IzumiScoptLauncherArgs]] = {
//          Set(
//            new ParserExtenstion[IzumiScoptLauncherArgs] {
//              opt[Unit]("custom-parser").abbr("custom")
//                .text("external option parser")
//                .action { (_, c) =>
//                  c.roles = List(testRole)
//                  c
//                }
//            }
//          )
//        }
//
//        override val pluginConfig: PluginLoaderDefaultImpl.PluginConfig = PluginConfig(
//          debug = false
//          , packagesEnabled = Seq(s"$thisPkg.test")
//          , packagesDisabled = Seq.empty
//        )
//
//        override protected def start(context: Locator): Unit = {
//          super.start(context)
//          val cfg = commandlineSetup(Array("-custom"))
//          assert(cfg.roles.nonEmpty)
//          assert(cfg.roles.head == testRole)
//          ()
//        }
//      }.main(Array.empty)
//    }
//  }
//
//  private def verifyConfig(context: Locator) = {
//    val version = context.get[ArtifactVersion]("launcher-version")
//    val justConfig = Paths.get("target", "config-dump", s"testservice-${version.version}.conf").toFile
//    val minConfig = Paths.get("target", "config-dump", s"testservice-minimized-${version.version}.conf").toFile
//
//    try {
//      assert(justConfig.exists())
//      assert(minConfig.exists())
//    } finally {
//      justConfig.delete().discard()
//      minConfig.delete().discard()
//    }
//  }
//
//  private val overrides = Map(
//    "testservice.systemPropInt" -> "265"
//    , "testservice.systemPropList.0" -> "111"
//    , "testservice.systemPropList.1" -> "222"
//  )
//
//}
