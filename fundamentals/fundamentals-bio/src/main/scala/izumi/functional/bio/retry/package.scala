package izumi.functional.bio

package object retry {

  type Scheduler3[F[-_, +_, +_]] = Scheduler2[F[Any, +_, +_]]
  object Scheduler3 {
    @inline def apply[F[-_, +_, +_]: Scheduler3]: Scheduler3[F] = implicitly
  }

}
