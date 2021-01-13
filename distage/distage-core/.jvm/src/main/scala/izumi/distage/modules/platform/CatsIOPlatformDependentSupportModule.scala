package izumi.distage.modules.platform

import cats.effect.{Blocker, IO, Sync}
import izumi.distage.model.definition.ModuleDef

private[modules] trait CatsIOPlatformDependentSupportModule extends ModuleDef {
  make[Blocker].fromResource(Blocker[IO](_: Sync[IO]))
}
