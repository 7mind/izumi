package izumi.functional.bio

import izumi.functional.bio.DivergenceHelper.{Divergent, Nondivergent}

trait RootInstancesLowPriorityVersionSpecific {
  @inline implicit final def Convert3To2[C[f[-_, +_, +_]] <: RootBifunctor[f], FR[-_, +_, +_], R0](
    implicit BifunctorPlus: C[FR] { type Divergence = Nondivergent }
  ): Divergent.Of[C[λ[(`-R`, `+E`, `+A`) => FR[R0, E, A]]]] = {
    Divergent(cast3To2[C, FR, R0](BifunctorPlus))
  }
}

trait BlockingIOLowPriorityVersionSpecific {

  @inline implicit final def blockingConvert3To2[C[f[-_, +_, +_]] <: BlockingIO3[f], FR[-_, +_, +_], R](
    implicit BlockingIO3: C[FR] { type Divergence = Nondivergent }
  ): Divergent.Of[BlockingIO2[FR[R, +_, +_]]] = {
    Divergent(cast3To2[C, FR, R](BlockingIO3))
  }

}
