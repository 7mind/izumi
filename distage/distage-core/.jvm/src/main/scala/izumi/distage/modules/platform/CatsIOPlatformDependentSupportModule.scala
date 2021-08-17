package izumi.distage.modules.platform

import cats.effect.{IO, Sync}
import izumi.distage.model.definition.ModuleDef
import cats.effect.Resource

private[modules] trait CatsIOPlatformDependentSupportModule extends ModuleDef {
  make[Blocker].fromResource(Resource.unit[IO](_: Sync[IO]))
}
