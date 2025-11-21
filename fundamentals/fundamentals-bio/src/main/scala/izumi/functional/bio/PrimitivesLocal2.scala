package izumi.functional.bio

import izumi.functional.bio.data.~>
import izumi.functional.bio.impl.PrimitivesLocalFromCatsIO
import izumi.fundamentals.orphans.`zio.ZIO`

trait PrimitivesLocal2[F[+_, +_]] extends PrimitivesLocalInstances {
  def mkFiberRef[A](a: A): F[Nothing, FiberRef2[F, A]]
  def mkFiberLocal[A](a: A): F[Nothing, FiberLocal2[F, A]] = mkFiberRef(a)
}
object PrimitivesLocal2 {
  @inline def apply[F[+_, +_]: PrimitivesLocal2]: PrimitivesLocal2[F] = implicitly

  def PrimitivesFromCatsIO[F[+_, +_]: Panic2](fromIO: cats.effect.IO ~> F[Throwable, _]) = new PrimitivesLocalFromCatsIO[F](fromIO)
}

private[bio] sealed trait PrimitivesLocalInstances
object PrimitivesLocalInstances extends PrimitivesLocalInstancesLowPriority {
  @inline implicit def PrimitivesZio[F[-_, +_, +_]: `zio.ZIO`]: PrimitivesLocal2[F[Any, +_, +_]] = impl.PrimitivesLocalZio.asInstanceOf[PrimitivesLocal2[F[Any, +_, +_]]]
}

sealed trait PrimitivesLocalInstancesLowPriority {
  @inline implicit def PrimitivesZioR[F[-_, +_, +_]: `zio.ZIO`, R]: PrimitivesLocal2[F[R, +_, +_]] = impl.PrimitivesLocalZio.asInstanceOf[PrimitivesLocal2[F[R, +_, +_]]]
}
