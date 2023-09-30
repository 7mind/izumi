package izumi.functional.bio

import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}

trait RootInstancesLowPriorityVersionSpecific {
  @inline implicit final def Convert3To2[C[f[-_, +_, +_]] <: RootBifunctor[f], FR[-_, +_, +_], R0, T](
    implicit BifunctorPlus: C[FR] {
      type Divergence = Nondivergent; type IsPredefined = T // `IsPredefined = T` is required only on Scala 3
    }
  ): Divergent.Of[C[λ[(`-R`, `+E`, `+A`) => FR[R0, E, A]]]] { type IsPredefined = T } = {
    BifunctorPlus.asInstanceOf[Divergent.Of[C[λ[(`-R`, `+E`, `+A`) => FR[R0, E, A]]]] { type IsPredefined = T }]
  }
}

trait BlockingIOLowPriorityVersionSpecific {

  @inline implicit final def blockingConvert3To2[C[f[-_, +_, +_]] <: BlockingIO3[f], FR[-_, +_, +_], R, T](
    implicit BlockingIO3: C[FR] {
      type Divergence = Nondivergent; type IsPredefined = T // `IsPredefined = T` is required only on Scala 3
    }
  ): Divergent.Of[BlockingIO2[FR[R, +_, +_]]] { type IsPredefined = T } = {
    BlockingIO3.asInstanceOf[Divergent.Of[BlockingIO2[FR[R, +_, +_]]] { type IsPredefined = T }]
  }

}
