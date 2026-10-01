package izumi.distage.modules.typeclass

import izumi.distage.model.definition.ModuleDef
import izumi.reflect.Tag

/**
  * Spike stand-in on Scala Native while zio-interop-cats publishes no Native artifact.
  * The real module binds `cats.effect.kernel.Async` and `cats.Parallel` for ZIO through `zio.interop.catz`.
  * `DefaultModule` reaches this module only through `zio.interop.CatsIOResourceSyntax` orphan evidence,
  * which cannot resolve without zio-interop-cats on the classpath, so this stand-in is unreachable on Native.
  */
class ZIOCatsEffectInstancesModule[R: Tag] extends ModuleDef

object ZIOCatsEffectInstancesModule {
  def apply[R: Tag]: ZIOCatsEffectInstancesModule[R] = new ZIOCatsEffectInstancesModule[R]
}
