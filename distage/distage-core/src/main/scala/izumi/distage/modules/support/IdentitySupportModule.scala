package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio.{Bifunctorized, Clock1, Clock2, Entropy1, Entropy2, IO2, Primitives2, SyncSafe1, SyncSafe2}
import izumi.fundamentals.platform.functional.Identity
import izumi.reflect.{TagK, TagKK}

object IdentitySupportModule extends IdentitySupportModule

/**
  * `Identity` effect type (aka no effect type / imperative Scala) support for `distage` resources, effects, roles & tests
  *
  * Adds [[izumi.functional.bio]] bifunctor BIO instances for [[Bifunctorized.IdentityBifunctorized]],
  * the MiniBIO-backed lawful carrier for `Identity` (no effect type / imperative Scala).
  *
  * Note: `TagK[Identity]` is still registered because user-facing entry points may construct
  * `Lifecycle[Identity, A]` / `Subcontext[Identity, A]` (legacy monofunctor surface). Internal
  * runtime always routes through `Bifunctorized.IdentityBifunctorized`.
  */
trait IdentitySupportModule extends ModuleDef {
  addImplicit[TagK[Identity]]
  addImplicit[TagKK[Bifunctorized.IdentityBifunctorized]]

  // BIO bifunctor for IdentityBifunctorized (MiniBIO-backed)
  addImplicit[IO2[Bifunctorized.IdentityBifunctorized]]
  addImplicit[Primitives2[Bifunctorized.IdentityBifunctorized]]

  // Wall-clock / entropy services for Identity (no effect)
  make[Clock1[Identity]].fromValue(Clock1.Standard)
  make[Entropy1[Identity]].fromValue(Entropy1.Standard)

  // ... and lifted into the bifunctor carrier for code that runs through IdentityBifunctorized
  make[SyncSafe2[Bifunctorized.IdentityBifunctorized]].from {
    SyncSafe1.fromBIO(using _: IO2[Bifunctorized.IdentityBifunctorized])
  }
  make[Clock2[Bifunctorized.IdentityBifunctorized]].from {
    (c: Clock1[Identity], s: SyncSafe2[Bifunctorized.IdentityBifunctorized]) => Clock1.fromImpure(c)(using s)
  }
  make[Entropy2[Bifunctorized.IdentityBifunctorized]].from {
    (e: Entropy1[Identity], s: SyncSafe2[Bifunctorized.IdentityBifunctorized]) => Entropy1.fromImpure(e)(using s)
  }
}
