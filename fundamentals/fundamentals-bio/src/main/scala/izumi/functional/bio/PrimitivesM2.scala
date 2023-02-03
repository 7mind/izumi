package izumi.functional.bio

import izumi.functional.bio.data.Isomorphism2
import izumi.functional.bio.impl.PrimitivesMFromBIO
import izumi.fundamentals.orphans.`zio.ZIO`

trait PrimitivesM2[F[+_, +_]] extends PrimitivesMInstances {
  def mkRefM[A](a: A): F[Nothing, RefM2[F, A]]
  def mkMutex: F[Nothing, Mutex2[F]]
}
object PrimitivesM2 {
  def apply[F[+_, +_]: PrimitivesM2]: PrimitivesM2[F] = implicitly

  implicit final class PrimitivesM2Ops[F[+_, +_]](private val self: PrimitivesM2[F]) extends AnyVal {
    def mapK[G[+_, +_]](fg: F Isomorphism2 G)(implicit G: Functor2[G]): PrimitivesM2[G] = new PrimitivesM2[G] {
      def mkRefM[A](a: A): G[Nothing, RefM2[G, A]] = fg.to(self.mkRefM(a)).map(_.imapK(fg))
      def mkMutex: G[Nothing, Mutex2[G]] = fg.to(self.mkMutex).map(_.imapK(fg))
    }
  }
}

private[bio] sealed trait PrimitivesMInstances
object PrimitivesMInstances extends PrimitivesMLowPriorityInstances {
  @inline implicit def PrimitivesZio[F[-_, +_, +_]: `zio.ZIO`]: PrimitivesM2[F[Any, +_, +_]] = impl.PrimitivesMZio.asInstanceOf[PrimitivesM3[F]]

  // do not use PrimitivesM3 alias here because it confuses both Scala 2 and Scala 3 typechecker in certain cases
//  @inline implicit def PrimitivesZio[F[-_, +_, +_]: `zio.ZIO`]: PrimitivesM3[F] = impl.PrimitivesMZio.asInstanceOf[PrimitivesM3[F]]
}

sealed trait PrimitivesMLowPriorityInstances {
  @inline implicit def PrimitivesFromBIO[F[+_, +_]: Bracket2: Primitives2]: PrimitivesM2[F] = new PrimitivesMFromBIO[F]
}
