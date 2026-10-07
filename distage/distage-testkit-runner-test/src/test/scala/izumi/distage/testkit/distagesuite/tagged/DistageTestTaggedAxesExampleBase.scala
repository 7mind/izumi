package izumi.distage.testkit.distagesuite.tagged

import distage.DIKey
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.distagesuite.tagged.DistageTestTaggedAxesExampleBase.{DepsCounters, DummyDep, PrdDep}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.runner.spec.SpecZIO
import izumi.fundamentals.assertions.bio.BIOAssertionSuspension.*

import java.util.concurrent.atomic.AtomicBoolean

abstract class DistageTestTaggedAxesExampleBase extends SpecZIO {
  override protected def config: TestConfig = super.config.copy(
    forcedRoots = Map(
      Set(Repo.Prod) -> Set(DIKey[PrdDep]),
      Set(Repo.Dummy) -> Set(DIKey[DummyDep]),
    ),
    pluginConfig = PluginConfig.const(new izumi.distage.plugins.PluginDef {
      make[PrdDep]
      make[DummyDep]
      make[DepsCounters]
    }),
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
      assert2[zio.IO](counter.dummy.get) *> assert2[zio.IO](!counter.prod.get)
  }
}

class DistageTestTaggedAxesExampleProd extends DistageTestTaggedAxesExampleBase {
  override protected def config: TestConfig = super.config.copy(activation = super.config.activation + (Repo -> Repo.Prod))
  "forced roots should perform axis choose" in {
    (counter: DepsCounters) =>
      assert2[zio.IO](counter.prod.get) *> assert2[zio.IO](!counter.dummy.get)
  }
}
