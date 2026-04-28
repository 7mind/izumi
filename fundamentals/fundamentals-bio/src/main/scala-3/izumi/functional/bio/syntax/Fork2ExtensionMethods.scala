package izumi.functional.bio.syntax

import izumi.functional.bio.{Fiber2, Fork2}

import scala.annotation.targetName

trait Fork2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Fork2[F]) {
    @targetName("forkExt")
    def fork: F[Nothing, Fiber2[F, E, A]] = F.fork(r)
  }
}
