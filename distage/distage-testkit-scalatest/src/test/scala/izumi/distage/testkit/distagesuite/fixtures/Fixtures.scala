package izumi.distage.testkit.distagesuite.fixtures

import java.util.concurrent.atomic.AtomicInteger

import cats.effect.{IO => CIO}
import distage.TagK
import izumi.distage.effect.modules.{CatsDIEffectModule, MonixDIEffectModule, ZIODIEffectModule}
import izumi.distage.framework.model.IntegrationCheck
import izumi.distage.model.definition.DIResource
import izumi.distage.model.definition.StandardAxis.Env
import izumi.distage.model.effect.DIEffect
import izumi.distage.plugins.PluginDef
import izumi.fundamentals.platform.functional.Identity
import izumi.fundamentals.platform.integration.ResourceCheck
import zio.Task

import scala.collection.mutable

object MonadPlugin extends PluginDef with CatsDIEffectModule with MonixDIEffectModule with ZIODIEffectModule

object MockAppCatsIOPlugin extends MockAppPlugin[CIO]
object MockAppZioPlugin extends MockAppPlugin[Task]
object MockAppIdPlugin extends MockAppPlugin[Identity]

abstract class MockAppPlugin[F[_]: TagK] extends PluginDef {
  make[MockPostgresDriver[F]]
  make[MockUserRepository[F]]
  make[MockPostgresCheck[F]]
  make[MockRedis[F]]
  make[MockCache[F]]
  make[MockCachedUserService[F]]
  make[ApplePaymentProvider[F]]
  make[ActiveComponent].from(TestActiveComponent).tagged(Env.Test)
  make[ActiveComponent].from(ProdActiveComponent).tagged(Env.Prod)
}

trait ActiveComponent
case object TestActiveComponent extends ActiveComponent
case object ProdActiveComponent extends ActiveComponent

class MockPostgresCheck[F[_]]() extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck = ResourceCheck.Success()
}

class MockPostgresDriver[F[_]](val check: MockPostgresCheck[F])

class MockRedis[F[_]]()

class MockUserRepository[F[_]](val pg: MockPostgresDriver[F])

class MockCache[F[_]](val redis: MockRedis[F]) extends IntegrationCheck {
  locally {
    val integer = MockCache.instanceCounter.getOrElseUpdate(redis, new AtomicInteger(0))
    if (integer.incrementAndGet() > 2) { // one instance per each monad
      throw new RuntimeException(s"Something is wrong with memoization: $integer instances were created")
    }
  }
  override def resourcesAvailable(): ResourceCheck = ResourceCheck.Success()
}

object MockCache {
  val instanceCounter = mutable.Map[AnyRef, AtomicInteger]()
}

class ApplePaymentProvider[F[_]] extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck = ResourceCheck.ResourceUnavailable("Test", None)
}

class MockCachedUserService[F[_]](val users: MockUserRepository[F], val cache: MockCache[F])

class ForcedRootProbe {
  var started = false
}
class ForcedRootResource[F[_]: DIEffect](forcedRootProbe: ForcedRootProbe)
  extends DIResource.SelfNoClose[F, ForcedRootResource[F]] {
  override def acquire: F[Unit] = DIEffect[F].maybeSuspend(forcedRootProbe.started = true)
}
