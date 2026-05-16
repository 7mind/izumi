package izumi.distage.testkit.distagesuite.fixtures

import java.util.concurrent.atomic.AtomicInteger

import cats.effect.IO as CIO
import distage.TagKK
import izumi.distage.model.provisioning.IntegrationCheck
import izumi.distage.model.definition.Lifecycle
import izumi.distage.model.definition.StandardAxis.Mode
import izumi.functional.bio.{Bifunctorized, IO2}
import izumi.functional.bio.CatsToBIOConversions.*
import izumi.distage.plugins.PluginDef
import izumi.fundamentals.platform.integration.ResourceCheck

import scala.collection.mutable

object MockAppCatsIOPlugin extends MockAppPlugin[Bifunctorized[CIO, +_, +_]]
object MockAppZioPlugin extends MockAppPlugin[zio.IO]
object MockAppIdPlugin extends MockAppPlugin[Bifunctorized.IdentityBifunctorized]
object MockAppZioZEnvPlugin extends MockAppPlugin[zio.ZIO[Int, +_, +_]]

abstract class MockAppPlugin[F[+_, +_]: TagKK: IO2] extends PluginDef {
  make[MockPostgresDriver[F]]
  make[MockUserRepository[F]]
  make[MockPostgresCheck[F]]
  make[MockRedis[F]]
  make[MockCache[F]]
  make[MockCachedUserService[F]]
  make[UnavailableIntegrationCheck[F]]
  make[ActiveComponent].fromValue(TestActiveComponent).tagged(Mode.Test)
  make[ActiveComponent].fromValue(ProdActiveComponent).tagged(Mode.Prod)
}

trait ActiveComponent
case object TestActiveComponent extends ActiveComponent
case object ProdActiveComponent extends ActiveComponent

class MockPostgresCheck[F[+_, +_]: IO2]() extends IntegrationCheck[F[Throwable, _]] {
  override def resourcesAvailable(): F[Throwable, ResourceCheck] = IO2[F].pure(ResourceCheck.Success())
}

class MockPostgresDriver[F[+_, +_]](val check: MockPostgresCheck[F])

class MockRedis[F[+_, +_]]()

class MockUserRepository[F[+_, +_]](val pg: MockPostgresDriver[F])

class MockCache[F[+_, +_]: IO2](val redis: MockRedis[F]) extends IntegrationCheck[F[Throwable, _]] {
  locally {
    val integer = MockCache.instanceCounter.getOrElseUpdate(redis, new AtomicInteger(0))
    if (integer.incrementAndGet() > 2) { // one instance per each monad
      throw new RuntimeException(s"Something is wrong with memoization: $integer instances were created")
    }
  }
  override def resourcesAvailable(): F[Throwable, ResourceCheck] = IO2[F].pure(ResourceCheck.Success())
}

object MockCache {
  val instanceCounter = mutable.Map[AnyRef, AtomicInteger]()
}

class UnavailableIntegrationCheck[F[+_, +_]: IO2] extends IntegrationCheck[F[Throwable, _]] {
  override def resourcesAvailable(): F[Throwable, ResourceCheck] =
    IO2[F].pure(ResourceCheck.ResourceUnavailable("Dummy unavailable resource for testing purposes", None))
}

class MockCachedUserService[F[+_, +_]](val users: MockUserRepository[F], val cache: MockCache[F])

class ForcedRootProbe {
  var started = false
}
class ForcedRootResource[F[+_, +_]: IO2](forcedRootProbe: ForcedRootProbe) extends Lifecycle.SelfNoClose[F, Nothing, ForcedRootResource[F]] {
  override def acquire: F[Nothing, Unit] = IO2[F].sync(forcedRootProbe.started = true)
}
