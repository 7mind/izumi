package izumi

import izumi.distage.model.definition.{Lifecycle2, Lifecycle3}
import izumi.functional.bio.{Functor2, Functor3}
import org.scalatest.wordspec.AnyWordSpec

class LifecycleIzumiInstancesTest extends AnyWordSpec {
  "Summon Functor2/Functor3 instances for Lifecycle" in {
    def t2[F[+_, +_]: Functor2]: Functor2[Lifecycle2[F, +_, +_]] = {
      Functor2[Lifecycle2[F, +_, +_]]
    }

    def t3[F[-_, +_, +_]: Functor3]: Functor3[Lifecycle3[F, -_, +_, +_]] = {
      Functor3[Lifecycle3[F, -_, +_, +_]]
    }

    t2[zio.IO]
    t2[monix.bio.IO]

    t3[zio.ZIO]
  }

}
