package izumi.functional.bio.syntax

import izumi.functional.bio.{Exit, Panic2}

import scala.annotation.targetName

trait Panic2ExtensionMethods {
  extension [F[+_, +_], E, A](r: F[E, A])(using F: Panic2[F]) {
    @targetName("sandboxExt")
    def sandbox: F[Exit.FailureUninterrupted[E], A] = F.sandbox(r)

    @targetName("sandboxExitExt")
    def sandboxExit: F[Nothing, Exit.Uninterrupted[E, A]] = F.sandboxExit(r)

    /**
      * Catch all _defects_ in this effect and convert them to Throwable
      * Example:
      *
      * {{{
      *   F.pure(1)
      *     .map(_ => ???)
      *     .sandboxThrowable
      *     .catchAll(_ => IO2(println("Caught error!")))
      * }}}
      */
    @targetName("sandboxToThrowableExt")
    def sandboxToThrowable(using ev: E <:< Throwable): F[Throwable, A] =
      F.leftMap(F.sandbox(r))(_.toThrowable)

    /** Convert Throwable typed error into a defect */
    @targetName("orTerminateExt")
    def orTerminate(using ev: E <:< Throwable): F[Nothing, A] = F.catchAll(r)(e => F.terminate(ev(e)))

    @targetName("uninterruptibleExt")
    def uninterruptible: F[E, A] = F.uninterruptible(r)
  }
}
