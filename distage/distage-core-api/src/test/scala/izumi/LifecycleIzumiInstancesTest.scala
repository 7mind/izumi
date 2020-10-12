package izumi

import izumi.distage.model.definition.Lifecycle
import izumi.functional.bio.{BIOApplicative, BIOApplicative3, BIOFunctor, BIOFunctor3}
import org.scalatest.wordspec.AnyWordSpec

class LifecycleIzumiInstancesTest extends AnyWordSpec {
  "Summon Functor2/Functor3 instances for Lifecycle" in {
    def t2[F[+_, +_]: BIOApplicative] = {
      type G[+E, +A] = Lifecycle[F[E, ?], A]
      BIOFunctor[G]
    }

    def t3[F[-_, +_, +_]: BIOApplicative3] = {
      type G[-R, +E, +A] = Lifecycle[F[R, E, ?], A]
      BIOFunctor3[G]
    }

    t2[zio.IO]
    t2[monix.bio.IO]

    t3[zio.ZIO]
  }

}
