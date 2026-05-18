package izumi.distage.testkit.spec

import distage.{Tag, TagKK}
import izumi.distage.model.providers.Functoid
import izumi.functional.bio.IO2
import izumi.fundamentals.platform.language.SourceFilePosition

trait DISyntaxBase[F[+_, +_]] {
  implicit def tagBIO: TagKK[F]

  protected def takeIO[A](function: Functoid[F[Throwable, A]], pos: SourceFilePosition): Unit

  protected final def takeAny(function: Functoid[Any], pos: SourceFilePosition): Unit = {
    val f: Functoid[F[Throwable, Any]] = function.flatAp {
      (F: IO2[F]) => (a: Any) =>
        F.pure(a)
    }

    takeIO(f, pos)
  }

  protected final def takeFunIO[A, T: Tag](function: T => F[Throwable, A], pos: SourceFilePosition): Unit = {
    takeIO(function.asInstanceOf[T => F[Throwable, Any]], pos)
  }

  protected final def takeFunAny[T: Tag](function: T => Any, pos: SourceFilePosition): Unit = {
    takeAny(function, pos)
  }
}
