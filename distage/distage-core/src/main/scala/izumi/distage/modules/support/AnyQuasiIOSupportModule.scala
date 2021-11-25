package izumi.distage.modules.support

import izumi.reflect.TagK
import izumi.distage.model.definition.{Module, ModuleDef}
import izumi.distage.model.effect.{QuasiApplicative, QuasiAsync, QuasiIO, QuasiIORunner}

object AnyQuasiIOSupportModule {
  def withImplicits[F[_]: TagK: QuasiIO: QuasiAsync: QuasiIORunner]: Module = new ModuleDef {
    addImplicit[QuasiIO[F]]
    addImplicit[QuasiAsync[F]]
    addImplicit[QuasiApplicative[F]]
    addImplicit[QuasiIORunner[F]]
  }
}
