package izumi.distage.roles.test

import cats.effect.IO
import izumi.distage.plugins.PluginConfig
import izumi.distage.roles.launcher.AppShutdownStrategy.ImmediateExitShutdownStrategy
import izumi.distage.roles.launcher.{AppShutdownStrategy, RoleAppLauncher}
import izumi.distage.roles.test.fixtures.TestPlugin
import izumi.fundamentals.platform.language.SourcePackageMaterializer.thisPkg

import scala.reflect.runtime.universe.weakTypeOf

class TestLauncher extends RoleAppLauncher.LauncherF[IO] {
  override protected def pluginConfig: PluginConfig =
    PluginConfig.cached(Seq(s"$thisPkg.fixtures"))
  override protected val shutdownStrategy: AppShutdownStrategy[IO] = new ImmediateExitShutdownStrategy()
}

object TestApp extends App {
  def x = { ???; () }
  val p = new TestPlugin
  println(weakTypeOf[p.RecompilationToken].dealias)
//  println(weakTypeOf[p.RecompilationToken].dealias.widen)
//  println(weakTypeOf[p.RecompilationToken].dealias.widen.baseClasses)
//  println(weakTypeOf[p.RecompilationToken].dealias.widen.typeArgs)
//  println(weakTypeOf[p.RecompilationToken].dealias.widen.typeParams)
//  println(valueOf[p.RecompilationToken])
}
