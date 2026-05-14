package izumi

import izumi.distage.model.definition.Lifecycle2
import izumi.functional.bio.{Applicative2, Functor2, IO2, Monad2, Primitives2}
import org.scalatest.wordspec.AnyWordSpec

class LifecycleIzumiInstancesTest extends AnyWordSpec {
  "Summon Monad2 instances for Lifecycle" in {
    def t2[F[+_, +_]: IO2: Primitives2]: Functor2[Lifecycle2[F, +_, +_]] = {
      Functor2[Lifecycle2[F, +_, +_]]
      Applicative2[Lifecycle2[F, +_, +_]]
      Monad2[Lifecycle2[F, +_, +_]]
    }

    t2[zio.IO]
//    t2[monix.bio.IO]
  }

}
