package izumi.functional.bio

import izumi.functional.bio.DivergenceHelper.Nondivergent

trait DivergenceHelper {
  type Divergence = Nondivergent
}
object DivergenceHelper {
  type Divergent
  type Nondivergent
  object Divergent {
    type Of[A] = A { type Divergence = Divergent }
    @inline def apply[A <: DivergenceHelper, A1 >: A](a: A): Divergent.Of[A1] = a.asInstanceOf[Divergent.Of[A1]]
  }
  object Nondivergent {
    type Of[A] = A { type Divergence = Nondivergent }
  }
}
