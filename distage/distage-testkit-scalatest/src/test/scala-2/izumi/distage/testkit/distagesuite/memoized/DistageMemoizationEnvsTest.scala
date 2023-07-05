package izumi.distage.testkit.distagesuite.memoized

import java.util.UUID

import distage.DIKey
import distage.plugins.PluginDef
import izumi.distage.model.definition.StandardAxis.Repo
import izumi.distage.model.providers.Functoid
import izumi.distage.testkit.distagesuite.memoized.MemoizationEnv.{MemoizedInstance, MemoizedLevel1, MemoizedLevel2, MemoizedLevel3, TestInstance}
import izumi.distage.testkit.model.TestConfig
import izumi.distage.testkit.scalatest.{AssertZIO, Spec3}
import org.scalatest.Assertion
import zio.{IO, ZIO}

import scala.collection.mutable

// TODO: for some mysterious reason these tests fail on Scala 3. That must be fixed.

object MemoizationEnv {
  final case class MemoizedInstance(uuid: UUID)
  final case class TestInstance(uuid: UUID)
  final val anotherTestInstance: TestInstance = TestInstance(UUID.randomUUID())
  final val memoizedInstance: mutable.HashSet[MemoizedInstance] = mutable.HashSet.empty

  final case class MemoizedLevel1(UUID: UUID)
  final val memoizedLevel1: mutable.HashSet[MemoizedLevel1] = mutable.HashSet.empty

  final case class MemoizedLevel2(UUID: UUID)
  final val memoizedLevel2: mutable.HashSet[MemoizedLevel2] = mutable.HashSet.empty

  final case class MemoizedLevel3(UUID: UUID)
  final val memoizedLevel3: mutable.HashSet[MemoizedLevel3] = mutable.HashSet.empty

  def makeInstance[T](set: mutable.HashSet[T])(ctor: UUID => T): T = {
    val instance = ctor(UUID.randomUUID())
    set.synchronized {
      set += instance
    }
    instance
  }
}

abstract class DistageMemoizationEnvsTest extends Spec3[ZIO] with AssertZIO {
  override protected def config: TestConfig = {
    super.config
      .copy(
        memoizationRoots = Map(
          1 -> Set(DIKey[MemoizedInstance], DIKey.get[MemoizedLevel1]),
          2 -> Set(DIKey[MemoizedLevel2]),
        ),
        pluginConfig = super.config.pluginConfig.enablePackage("izumi.distage.testkit.distagesuite") ++ new PluginDef {
          make[MemoizedInstance].from {
            MemoizationEnv.makeInstance(MemoizationEnv.memoizedInstance)(MemoizationEnv.MemoizedInstance.apply)
          }
          make[MemoizedLevel1].from {
            MemoizationEnv.makeInstance(MemoizationEnv.memoizedLevel1)(MemoizationEnv.MemoizedLevel1.apply)
          }
          make[MemoizedLevel2].from {
            MemoizationEnv.makeInstance(MemoizationEnv.memoizedLevel2)(MemoizationEnv.MemoizedLevel2.apply)
          }
          make[MemoizedLevel3].from {
            MemoizationEnv.makeInstance(MemoizationEnv.memoizedLevel3)(MemoizationEnv.MemoizedLevel3.apply)
          }
          make[TestInstance].from(TestInstance(UUID.randomUUID()))
        },
        forcedRoots = Set(DIKey.get[MemoizedInstance], DIKey.get[MemoizedLevel1]),
        activation = distage.Activation(Repo -> Repo.Prod),
      )
  }

  val assertion: Functoid[IO[Nothing, Assertion]] = Functoid {
    (memoized: MemoizedInstance) =>
      assertIO(MemoizationEnv.memoizedInstance.toSet == Set(memoized))
  }
}

class SameLevel_1_2_First extends DistageMemoizationEnvsTest {
  "should have the same memoized instance 1" in assertion
  "should have the same memoized instance 2" in assertion
  "should have the same leveled instances" in {
    (l1: MemoizedLevel1, l2: MemoizedLevel2) =>
      assertIO(MemoizationEnv.memoizedLevel1.toSet == Set(l1)) *>
      assertIO(MemoizationEnv.memoizedLevel2.toSet == Set(l2))
  }
}

class SameLevel_1_2_Second extends DistageMemoizationEnvsTest {
  "should have the same memoized instance 1" in assertion
  "should have the same memoized instance 2" in assertion
  "should have the same leveled instances" in {
    (l1: MemoizedLevel1, l2: MemoizedLevel2) =>
      assertIO(MemoizationEnv.memoizedLevel1.toSet == Set(l1)) *>
      assertIO(MemoizationEnv.memoizedLevel2.toSet == Set(l2))
  }
}

class SameLevel_1_WithoutLastMemoizationLevel extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super.config.copy(
      memoizationRoots = Map(
        1 -> Set(DIKey.get[MemoizedInstance], DIKey.get[MemoizedLevel1])
      ),
      pluginConfig = super.config.pluginConfig overriddenBy new PluginDef {
        make[MemoizedLevel2].from(MemoizedLevel2(UUID.randomUUID()))
      },
      activation = distage.Activation(Repo -> Repo.Prod),
    )
  }

  "should have the same memoized instance" in assertion
  "should have different level 2 instances" in {
    (l1: MemoizedLevel1, l2: MemoizedLevel2) =>
      assertIO(MemoizationEnv.memoizedLevel1.toSet == Set(l1)) *>
      assertIO(MemoizationEnv.memoizedLevel2.toSet != Set(l2))
  }
}

class SameLevel_1_2_WithAdditionalLevel3 extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super.config.copy(
      memoizationRoots = super.config.memoizationRoots ++
        Set(DIKey[MemoizedLevel3]),
      activation = distage.Activation(Repo -> Repo.Prod),
    )
  }

  "should have the same memoized instance" in assertion
  "should have same level 1-2 instance" in {
    (l1: MemoizedLevel1, l2: MemoizedLevel2, _: MemoizedLevel3) =>
      assertIO(MemoizationEnv.memoizedLevel1.toSet == Set(l1)) *>
      assertIO(MemoizationEnv.memoizedLevel2.toSet == Set(l2))
  }
}

class SameLevel_1_WithModuleOverride extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super.config.copy(
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

class SameLevel_1_WithActivationsOverride extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super.config.copy(activation = distage.Activation.empty)
  }
  "should have the same memoized instance even if activation was overriden" in assertion
}

trait DifferentLevelsWithLevel1 extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super.config.copy(
      pluginConfig = super.config.pluginConfig overriddenBy new PluginDef {
        make[MemoizedInstance].from(MemoizedInstance(UUID.randomUUID()))
        make[MemoizedLevel1].from(MemoizedLevel1(UUID.randomUUID()))
        make[MemoizedLevel2].from(MemoizedLevel2(UUID.randomUUID()))
      }
    )
  }
}

class DifferentLevelsWithLevel1InstanceOverride1 extends DifferentLevelsWithLevel1 {
  "should have different memoized instance" in {
    (memoized: MemoizedInstance, _: MemoizedLevel2) =>
      assertIO(MemoizationEnv.memoizedInstance.toSet != Set(memoized))
  }
}

class DifferentLevelsWithLevel1InstanceOverride2 extends DifferentLevelsWithLevel1 {
  override protected def config: TestConfig = {
    super.config.copy(
      pluginConfig = super.config.pluginConfig overriddenBy new PluginDef {
        make[MemoizedLevel2].from(MemoizedLevel2(UUID.randomUUID()))
      }
    )
  }
  "should have different memoized instance" in {
    (memoized: MemoizedInstance, _: MemoizedLevel2) =>
      assertIO(MemoizationEnv.memoizedInstance.toSet != Set(memoized))
  }
}

class SameLevel_1_WithLevel2InstanceOverride extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super.config.copy(
      pluginConfig = super.config.pluginConfig overriddenBy new PluginDef {
        make[MemoizedLevel2].from(MemoizedLevel2(UUID.randomUUID()))
      }
    )
  }
  "should have the same memoized instance of level 1 if the next levels instances have been changed" in assertion
}

class SameLevel_1_WithAdditionalButNotUsedMemoizedRoots extends DistageMemoizationEnvsTest {
  override protected def config: TestConfig = {
    super.config.copy(
      memoizationRoots = super.config.memoizationRoots ++ Set(DIKey.get[TestInstance])
    )
  }
  "should have the same memoized instance if memoized roots differs, but plan is similar" in assertion
}
