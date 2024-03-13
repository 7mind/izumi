package izumi

import izumi.distage.model.definition.Lifecycle2
import izumi.functional.bio.{Applicative2, Functor2, Monad2}
import izumi.functional.quasi.QuasiPrimitives
import org.scalatest.wordspec.AnyWordSpec

class LifecycleIzumiInstancesTest extends AnyWordSpec {
  "Summon Monad2 instances for Lifecycle" in {
    def t2[F[+_, +_]: Functor2](implicit P: QuasiPrimitives[F[Any, _]]): Functor2[Lifecycle2[F, +_, +_]] = {
      Functor2[Lifecycle2[F, +_, +_]]
      Applicative2[Lifecycle2[F, +_, +_]]
      Monad2[Lifecycle2[F, +_, +_]]
    }

    t2[zio.IO]
//    t2[monix.bio.IO]
  }

}
