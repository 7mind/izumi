package izumi.distage.modules.support

import izumi.distage.model.definition.ModuleDef
import izumi.functional.bio.{ApplicativeError2, Bifunctorized, Clock1, Clock2, Entropy1, Entropy2, IO2, Parallel2, Primitives2, SyncSafe1, SyncSafe2, UnsafeRun2, WeakTemporal2}
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
  addImplicit[Parallel2[Bifunctorized.IdentityBifunctorized]]
  // Expose IO2 also as ApplicativeError2 (IO2 <: ApplicativeError2). Required by
  // `DISyntaxBIOBase.takeBIO`, which summons `ApplicativeError2[F]` to lift `F[Any, _]` test bodies
  // into `F[Throwable, _]` via `leftMap`. The `using[IO2[...]]` clause forwards the existing
  // typeclass instance under the supertype slot — distage does not auto-derive supertype bindings.
  make[ApplicativeError2[Bifunctorized.IdentityBifunctorized]].using[IO2[Bifunctorized.IdentityBifunctorized]]
  // UnsafeRun2 for the MiniBIO-backed IdentityBifunctorized carrier — runs synchronously on the
  // calling thread. Required by the testkit per-test injector: `TestPlanner` registers
  // `UnsafeRun2[TestF]` as a root for every test, and for `SpecIdentity` tests the inner `TestF` is
  // `IdentityBifunctorized`.
  addImplicit[UnsafeRun2[Bifunctorized.IdentityBifunctorized]]

  // Temporal2 for the MiniBIO-backed IdentityBifunctorized carrier — `sleep` blocks the calling
  // thread via `Thread.sleep`, `timeout` runs the effect to completion. Restores the
  // pre-bifunctorization `QuasiTemporal[Identity]` capability used by Identity test variants
  // (`SpecIdentity` tests that exercise parallelism bounds via Thread.sleep).
  addImplicit[WeakTemporal2[Bifunctorized.IdentityBifunctorized]]

  // Wall-clock / entropy services for Identity (no effect)
  make[Clock1[Identity]].fromValue(Clock1.Standard)
  make[Entropy1[Identity]].fromValue(Entropy1.Standard)

  // SyncSafe1[Identity] — no-op "suspension" (Identity has no effect channel; eff: => A is evaluated eagerly).
  make[SyncSafe1[Identity]].fromValue(new SyncSafe1[Identity] {
    override def syncSafe[A](eff: => A): Identity[A] = eff
  })

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
