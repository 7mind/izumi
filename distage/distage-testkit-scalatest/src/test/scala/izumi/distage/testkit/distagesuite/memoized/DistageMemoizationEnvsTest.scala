package izumi.distage.testkit.distagesuite.memoized

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import distage.DIKey
import distage.plugins.PluginDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.distagesuite.memoized.MemoizationEnv.{MemoizedInstance, TestInstance}
import izumi.distage.testkit.scalatest.{AssertIO, DistageBIOEnvSpecScalatest}
import zio.ZIO

object MemoizationEnv {
  case class MemoizedInstance(uuid: UUID)
  case class TestInstance(uuid: UUID)
  val anotherTestInstance: TestInstance = TestInstance(UUID.randomUUID())
  val memoizedInstance: AtomicReference[Option[MemoizedInstance]] = new AtomicReference[Option[MemoizedInstance]](None)
}

abstract class DistageMemoizationEnvsTest extends DistageBIOEnvSpecScalatest[ZIO] with AssertIO {
  override protected def config: TestConfig = {
    super
      .config.copy(
        memoizationRoots = Set(DIKey.get[MemoizedInstance]),
        pluginConfig = super.config.pluginConfig.enablePackage("izumi.distage.testkit.distagesuite") ++ new PluginDef {
            make[MemoizedInstance].from {
              val instance = MemoizedInstance(UUID.randomUUID())
              MemoizationEnv.memoizedInstance.set(Some(instance))
              instance
            }
            make[TestInstance].from(TestInstance(UUID.randomUUID()))
          },
        activation = distage.Activation(Repo -> Repo.Prod),
      )
  }
}

class SameEnvModulesTest1 extends DistageMemoizationEnvsTest {
  "should have the same memoized instance 1" in {
    memoized: MemoizedInstance =>
      assertIO(MemoizationEnv.memoizedInstance.get.contains(memoized))
  }
  "should have the same memoized instance 2" in {
    memoized: MemoizedInstance =>
      assertIO(MemoizationEnv.memoizedInstance.get.contains(memoized))
  }
}

class SameEnvModulesTest2 extends DistageMemoizationEnvsTest {
  "should have the same memoized instance 1" in {
    memoized: MemoizedInstance =>
      assertIO(MemoizationEnv.memoizedInstance.get.contains(memoized))
  }
  "should have the same memoized instance 2" in {
    memoized: MemoizedInstance =>
      assertIO(MemoizationEnv.memoizedInstance.get.contains(memoized))
  }
}

class SameEnvWithModuleOverride extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super
      .config.copy(
        pluginConfig = super.config.pluginConfig overridenBy new PluginDef {
            make[TestInstance].from(MemoizationEnv.anotherTestInstance)
          }
      )
  }
  "should have the same memoized instance even if module was overriden" in {
    (memoized: MemoizedInstance, test: TestInstance) =>
      assertIO(MemoizationEnv.anotherTestInstance == test) *>
      assertIO(MemoizationEnv.memoizedInstance.get.contains(memoized))
  }
}

class SameEnvWithActivationsOverride extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super.config.copy(activation = distage.Activation.empty)
  }
  "should have the same memoized instance even if activation was overriden" in {
    memoized: MemoizedInstance =>
      assertIO(MemoizationEnv.memoizedInstance.get.contains(memoized))
  }
}

class DifferentEnvWithMemoizedRootOverride extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super
      .config.copy(
        pluginConfig = super.config.pluginConfig overridenBy new PluginDef {
            make[MemoizedInstance].from(MemoizedInstance(UUID.randomUUID()))
          }
      )
  }
  "should have different memoized instance" in {
    memoized: MemoizedInstance =>
      assertIO(!MemoizationEnv.memoizedInstance.get.contains(memoized))
  }
}

class SameEnvWithAdditionalButNotUsedMemoizedRoots extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super
      .config.copy(
        memoizationRoots = super.config.memoizationRoots ++ Set(DIKey.get[TestInstance])
      )
  }
  "should have the same memoized instance if memoized roots differs, but plan is similar" in {
    memoized: MemoizedInstance =>
      assertIO(MemoizationEnv.memoizedInstance.get.contains(memoized))
  }
}
