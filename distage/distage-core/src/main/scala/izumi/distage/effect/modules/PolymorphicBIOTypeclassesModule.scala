package izumi.distage.effect.modules

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio._
import izumi.reflect.TagKK

// FIXME: remove implicit
class PolymorphicBIOTypeclassesModule[F[+_, +_]: TagKK: BIOAsync: BIOFork: BIOTemporal: BIOPrimitives] extends ModuleDef {
  addImplicit[BIOFork[F]]
  addImplicit[SyncSafe2[F]]
  addImplicit[BIOPrimitives[F]]

  addImplicit[BIOFunctor[F]]
  addImplicit[BIOBifunctor[F]]
  addImplicit[BIOApplicative[F]]
  addImplicit[BIOGuarantee[F]]
  addImplicit[BIOApplicativeError[F]]
  addImplicit[BIOMonad[F]]
  addImplicit[BIOError[F]]
  addImplicit[BIOBracket[F]]
  addImplicit[BIOPanic[F]]
  addImplicit[BIO[F]]
  addImplicit[BIOParallel[F]]
  addImplicit[BIOAsync[F]]

  addImplicit[BIOTemporal[F]]
//  make[BIOTemporal[F]].from {
//    implicit r: zio.clock.Clock =>
//      implicitly[BIOTemporal[F]]
//  }
}

object PolymorphicBIOTypeclassesModule {
  // FIXME: remove implicit
  def apply[F[+_, +_]: TagKK: BIOAsync: BIOFork: BIOTemporal: BIOPrimitives]: PolymorphicBIOTypeclassesModule[F] = new PolymorphicBIOTypeclassesModule
}
