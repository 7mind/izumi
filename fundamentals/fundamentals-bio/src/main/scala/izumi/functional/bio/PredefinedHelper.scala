package izumi.functional.bio

import izumi.functional.bio.PredefinedHelper.NotPredefined

trait PredefinedHelper {
  type IsPredefined = NotPredefined
}
object PredefinedHelper {
  type Predefined
  type NotPredefined

  object Predefined {
    /**
      * Unlike for [[DivergenceHelper.Divergent.Of]] a contradictory
      * value is required here because we do not use a type variable
      * for `ConvertFrom*` conversions
      */
    type Of[A] = A { type IsPredefined = Predefined }
    @inline def apply[A <: PredefinedHelper, A1 >: A](a: A): Predefined.Of[A1] = a.asInstanceOf[Predefined.Of[A1]]
  }
  object NotPredefined {
    type Of[A] = A { type IsPredefined = NotPredefined }
  }
}
