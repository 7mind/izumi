package izumi.distage.testkit.spec

import distage.{Tag, TagK}
import izumi.distage.model.providers.Functoid
import izumi.functional.quasi.QuasiIO
import izumi.fundamentals.platform.language.SourceFilePosition

trait DISyntaxBase[F[_]] {
  implicit def tagMonoIO: TagK[F]

  protected def takeIO[A](function: Functoid[F[A]], pos: SourceFilePosition): Unit

  protected final def takeAny(function: Functoid[Any], pos: SourceFilePosition): Unit = {
    val f: Functoid[F[Any]] = function.flatAp {
      (F: QuasiIO[F]) => (a: Any) =>
        F.pure(a)
    }

    takeIO(f, pos)
  }

  protected final def takeFunIO[A, T: Tag](function: T => F[A], pos: SourceFilePosition): Unit = {
    takeIO(function.asInstanceOf[T => F[Any]], pos)
  }

  protected final def takeFunAny[T: Tag](function: T => Any, pos: SourceFilePosition): Unit = {
    takeAny(function, pos)
  }
}
