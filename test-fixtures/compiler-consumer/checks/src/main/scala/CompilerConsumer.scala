package izumi.fixtures.compiler

import izumi.distage.framework.{PlanCheck, PlanCheckConfig}
import izumi.fundamentals.platform.language.{ScalaRelease, ScalaReleaseMaterializer}

object CompilerConsumer {
  private val good = PlanCheck.assertAppCompileTime(ConsumerApp, PlanCheckConfig(checkConfig = false))
  private val warning = PlanCheck.assertAppCompileTime(MissingDependencyApp, PlanCheckConfig(checkConfig = false, onlyWarn = true))
  private val release = ScalaReleaseMaterializer.scalaRelease

  def main(args: Array[String]): Unit = {
    require(args.length == 2, "Expected compiler minor and patch")
    require(good.checkPassed, "Valid application must pass its compile-time plan check")
    require(!warning.checkPassed, "Missing dependency must fail its compile-time plan check")
    require(release == ScalaRelease.`3`(args(0).toInt, args(1).toInt), s"Compiler release: $release")
    good.assertAgainAtRuntime()
    println(s"COMPILER_CONSUMER_OK release=$release good=true missing=false")
  }
}
