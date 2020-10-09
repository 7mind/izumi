package izumi

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{BIOApplicative, BIOFunctor}
import org.scalatest.wordspec.AnyWordSpec

class LifecycleIzumiInstancesTest extends AnyWordSpec {
  "Summon Functor2/Functor3 instances for Lifecycle" in {
    def t[F[+_, +_]: BIOApplicative, E] = {
      //val F = BIOFunctor[Lifecycle.LifecycleFunctor2[F]]
      type L[x] = F[E, x]
      //val Func = BIOFunctor[Lifecycle[zio.ZIO[Any, , ?], ?]](???)
      val F = BIOFunctor[Lifecycle[L, ?]]
      F
    }
    t[zio.IO, Throwable]
  }

}
