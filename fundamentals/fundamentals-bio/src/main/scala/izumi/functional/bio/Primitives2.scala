package izumi.functional.bio

import izumi.functional.bio.data.{Morphism1, ~>>}
import izumi.functional.bio.impl.PrimitivesFromBIOAndCats
import izumi.fundamentals.orphans.`zio.ZIO`

trait Primitives2[F[+_, +_]] extends PrimitivesInstances {
  def mkRef[A](a: A): F[Nothing, Ref2[F, A]]
  def mkPromise[E, A]: F[Nothing, Promise2[F, E, A]]
  def mkSemaphore(permits: Long): F[Nothing, Semaphore2[F]]
  def mkLatch: F[Nothing, Promise2[F, Nothing, Unit]] = mkPromise[Nothing, Unit]
}
object Primitives2 {
  def apply[F[+_, +_]: Primitives2]: Primitives2[F] = implicitly

  implicit final class Primitives2Ops[F[+_, +_]](private val self: Primitives2[F]) extends AnyVal {
    def mapK[G[+_, +_]](fg: F ~>> G)(implicit G: Functor2[G]): Primitives2[G] = new Primitives2[G] {
      override def mkRef[A](a: A): G[Nothing, Ref2[G, A]] = fg(self.mkRef(a)).map(_.mapK(fg: Morphism1[F[Nothing, _], G[Nothing, _]]))
      override def mkPromise[E, A]: G[Nothing, Promise2[G, E, A]] = fg(self.mkPromise[E, A]).map(_.mapK(fg))
      override def mkSemaphore(permits: Long): G[Nothing, Semaphore2[G]] = fg(self.mkSemaphore(permits)).map(_.mapK(fg: Morphism1[F[Nothing, _], G[Nothing, _]]))
    }
  }

  @inline def PrimitivesFromCatsPrimitives[F[+_, +_]: Async2: Fork2]: Primitives2[F] = new PrimitivesFromBIOAndCats[F]
}

private[bio] sealed trait PrimitivesInstances
object PrimitivesInstances {
  @inline implicit def PrimitivesZio[F[-_, +_, +_]: `zio.ZIO`]: Primitives2[F[Any, +_, +_]] = impl.PrimitivesZio.asInstanceOf[Primitives3[F]]

  // do not use Primitives3 alias here because it confuses both Scala 2 and Scala 3 typechecker in certain cases
//  @inline implicit def PrimitivesZio[F[-_, +_, +_]: `zio.ZIO`]: Primitives3[F] = impl.PrimitivesZio.asInstanceOf[Primitives3[F]]
}
