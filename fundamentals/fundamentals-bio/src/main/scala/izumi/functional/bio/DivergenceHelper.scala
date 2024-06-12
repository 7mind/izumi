package izumi.functional.bio

import izumi.functional.bio.DivergenceHelper.Nondivergent

import scala.annotation.nowarn

trait DivergenceHelper {
  type Divergence = Nondivergent
}
object DivergenceHelper {
  type Nondivergent

  object Divergent {
    /**
      * Upcast `Divergence` parameter instead of setting
      * a contradictory value as in `{ type Divergence = Divergent }`
      *
      * This allows a Divergent implicit to be _less_ specific
      * than the non-divergent instances, while at the same time
      * not matching its own requirement of `{ type Divergence = Nondivergent }`
      *
      * Note: Upcasting Divergence also depends on returning a type
      * variable instead of a known type. `Divergent.Of[SyncSafe3[F]]`
      * will not fail the Nondivergent requirement, only `Divergent.Of[C[F]]` will.
      */
    type Of[A] = A { type Divergence }
    @inline def apply[A <: DivergenceHelper, A1 >: A](a: A): Divergent.Of[A1] = a.asInstanceOf[Divergent.Of[A1]]
  }

  /**
    * Nondivergent helper does not work. To make use of
    * divergence protection, conversion implicits should use type variables
    * for summoning, instead of the direct type itself, as in:
    *
    * {{{
    *   implicit def convert1To2[C[f[_]] <: SyncSafe1[f], F[_, _], E](implicit F: C[F[Nothing, _]] { type Divergence = Nondivergent }): Divergent.Of[C[F[E, _]]]
    * }}}
    *
    * Where using `Nondivergent.Of[C[F[Nothing, _]]]` or `SyncSafe1[F[Nothing, _]] { type Divergence = Nondivergent }`
    * both will not work.
    *
    * The structural refinement must be placed directly on
    * the `C` type variable, not through an alias to work.
    */
  @nowarn("msg=never used") private object Nondivergent {
    @nowarn("msg=never used") private type Of[A] = A { type Divergence = Nondivergent }
  }
}
