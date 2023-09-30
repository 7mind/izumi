package izumi.distage.testkit.distagesuite.tagged

import java.util.concurrent.atomic.AtomicBoolean

import distage.DIKey
import distage.plugins.PluginDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.testkit.distagesuite.tagged.DistageTestTaggedAxesExampleBase.{DepsCounters, DummyDep, PrdDep}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.{AssertZIO, Spec3}
import zio.ZIO

abstract class DistageTestTaggedAxesExampleBase extends Spec3[ZIO] with AssertZIO {
  override protected def config: TestConfig = super.config.copy(
    forcedRoots = Map(
      Set(Repo.Prod) -> Set(DIKey[PrdDep]),
      Set(Repo.Dummy) -> Set(DIKey[DummyDep]),
    ),
    pluginConfig = super.config.pluginConfig.enablePackage("izumi.distage.testkit.distagesuite") ++ new PluginDef {
      make[PrdDep]
      make[DummyDep]
      make[DepsCounters]
    },
  )
}

object DistageTestTaggedAxesExampleBase {
  final case class DummyDep(c: DepsCounters) {
    c.dummy.set(true)
  }
  final case class PrdDep(c: DepsCounters) {
    c.prod.set(true)
  }
  final case class DepsCounters() {
    val dummy = new AtomicBoolean(false)
    val prod = new AtomicBoolean(false)
  }
}

class DistageTestTaggedAxesExampleDummy extends DistageTestTaggedAxesExampleBase {
  override protected def config: TestConfig = super.config.copy(activation = super.config.activation + (Repo -> Repo.Dummy))
  "forced roots should perform axis choose" in {
    (counter: DepsCounters) =>
      assertIO(counter.dummy.get) *> assertIO(!counter.prod.get)
  }
}

class DistageTestTaggedAxesExampleProd extends DistageTestTaggedAxesExampleBase {
  override protected def config: TestConfig = super.config.copy(activation = super.config.activation + (Repo -> Repo.Prod))
  "forced roots should perform axis choose" in {
    (counter: DepsCounters) =>
      assertIO(counter.prod.get) *> assertIO(!counter.dummy.get)
  }
}
