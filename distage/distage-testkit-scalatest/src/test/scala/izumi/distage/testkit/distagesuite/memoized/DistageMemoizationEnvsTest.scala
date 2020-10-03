package izumi.distage.testkit.distagesuite.memoized

import java.util.UUID

import distage.DIKey
import distage.plugins.PluginDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.model.providers.Functoid
import izumi.distage.testkit.TestConfig
import izumi.distage.testkit.distagesuite.memoized.MemoizationEnv.{MemoizedInstance, TestInstance}
import izumi.distage.testkit.scalatest.{AssertIO, DistageBIOEnvSpecScalatest}
import org.scalatest.Assertion
import zio.{IO, ZIO}

import scala.collection.mutable

object MemoizationEnv {
  case class MemoizedInstance(uuid: UUID)
  case class TestInstance(uuid: UUID)
  final val anotherTestInstance: TestInstance = TestInstance(UUID.randomUUID())
  final val memoizedInstance: mutable.HashSet[MemoizedInstance] = mutable.HashSet.empty
}

abstract class DistageMemoizationEnvsTest extends DistageBIOEnvSpecScalatest[ZIO] with AssertIO {
  override protected def config: TestConfig = {
    super
      .config.copy(
        memoizationRoots = Set(DIKey.get[MemoizedInstance]),
        pluginConfig = super.config.pluginConfig.enablePackage("izumi.distage.testkit.distagesuite") ++ new PluginDef {
          make[MemoizedInstance].from {
            val instance = MemoizedInstance(UUID.randomUUID())
            MemoizationEnv.synchronized {
              MemoizationEnv.memoizedInstance += instance
            }
            instance
          }
          make[TestInstance].from(TestInstance(UUID.randomUUID()))
        },
        activation = distage.Activation(Repo -> Repo.Prod),
      )
  }

  val assertion: Functoid[IO[Nothing, Assertion]] = Functoid {
    memoized: MemoizedInstance =>
      assertIO(MemoizationEnv.memoizedInstance.toSet == Set(memoized))
  }
}

class SameEnvModulesTest1 extends DistageMemoizationEnvsTest {
  "should have the same memoized instance 1" in assertion
  "should have the same memoized instance 2" in assertion
}

class SameEnvModulesTest2 extends DistageMemoizationEnvsTest {
  "should have the same memoized instance 1" in assertion
  "should have the same memoized instance 2" in assertion
}

class SameEnvWithModuleOverride extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super
      .config.copy(
        pluginConfig = super.config.pluginConfig overriddenBy new PluginDef {
          make[TestInstance].from(MemoizationEnv.anotherTestInstance)
        }
      )
  }
  "should have the same memoized instance even if module was overriden" in {
    (memoized: MemoizedInstance, test: TestInstance) =>
      assertIO(MemoizationEnv.anotherTestInstance == test) *>
      assertIO(MemoizationEnv.memoizedInstance.toSet == Set(memoized))
  }
}

class SameEnvWithActivationsOverride extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super.config.copy(activation = distage.Activation.empty)
  }
  "should have the same memoized instance even if activation was overriden" in assertion
}

class DifferentEnvWithMemoizedRootOverride extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super
      .config.copy(
        pluginConfig = super.config.pluginConfig overriddenBy new PluginDef {
          make[MemoizedInstance].from(MemoizedInstance(UUID.randomUUID()))
        }
      )
  }
  "should have different memoized instance" in {
    memoized: MemoizedInstance =>
      assertIO(MemoizationEnv.memoizedInstance.toSet != Set(memoized))
  }
}

class SameEnvWithAdditionalButNotUsedMemoizedRoots extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super
      .config.copy(
        memoizationRoots = super.config.memoizationRoots ++ Set(DIKey.get[TestInstance])
      )
  }
  "should have the same memoized instance if memoized roots differs, but plan is similar" in assertion
}
