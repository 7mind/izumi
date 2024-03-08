package izumi.functional.bio

import cats.~>
import izumi.functional.bio.data.RestoreInterruption2

trait Panic2[F[+_, +_]] extends Bracket2[F] with PanicSyntax {
  def terminate(v: => Throwable): F[Nothing, Nothing]

  /** @note Will return either [[Exit.Error]] or [[Exit.Termination]] in the error channel.
   *       [[Exit.Interruption]] cannot be sandboxed. Use [[guaranteeOnInterrupt]] for cleanups on interruptions. */
  def sandbox[E, A](r: F[E, A]): F[Exit.Failure[E], A]

  /**
    * Signal interruption to this fiber.
    *
    * This is _NOT_ the same as
    *
    * {{{
    *   F.halt(Exit.Interrupted(Trace.forUnknownError))
    * }}}
    *
    * The code above exits with `Exit.Interrupted` failure *unconditionally*,
    * whereas [[sendInterruptToSelf]] will not exit when in an uninterruptible
    * region. Example:
    *
    * {{{
    *   F.uninterruptible {
    *     F.halt(Exit.Interrupted(Trace.forUnknownError)) *>
    *     F.sync(println("Hello!")) // interrupted above. Hello _not_ printed
    *   }
    * }}}
    *
    * But with `sendInterruptToSelf`:
    *
    * {{{
    *   F.uninterruptible {
    *     F.sendInterruptToSelf *>
    *     F.sync(println("Hello!")) // Hello IS printed.
    *   } *> F.sync(println("Impossible")) // interrupted immediately after `uninterruptible` block ends. Impossible _not_ printed
    * }}}
    *
    * @see
    *   - [[https://github.com/zio/interop-cats/issues/503]] - History of supporting this method in ZIO
    *   - [[https://github.com/zio/zio/issues/6911]] - related issue
    */
  def sendInterruptToSelf: F[Nothing, Unit]

  def uninterruptible[E, A](r: F[E, A]): F[E, A] = {
    uninterruptibleExcept(_ => r)
  }

  /**
    * Designate the effect uninterruptible, with exception of regions
    * in it that are specifically marked to restore previous interruptibility
    * status using the provided `RestoreInterruption` function
    *
    * @example
    *
    * {{{
    *   F.uninterruptibleExcept {
    *     restoreInterruption =>
    *       val workLoop = {
    *         importantWorkThatMustNotBeInterrupted() *>
    *         log.info("Taking a break for a second, you can interrupt me while I wait!") *>
    *         restoreInterruption.apply {
    *           F.sleep(1.second)
    *            .guaranteeOnInterrupt(_ => log.info("Got interrupted!"))
    *         } *>
    *         log.info("No interruptions, going back to work!") *>
    *         workLoop
    *       }
    *
    *       workLoop
    *   }
    * }}}
    *
    * @note
    *
    * Interruptibility status will be restored to what it was in the outer region,
    * so if the outer region was also uninterruptible, the provided `RestoreInterruption`
    * will have no effect. e.g. the expression
    * `F.uninterruptible { F.uninterruptibleExcept { restore => restore(F.sleep(1.second)) }`
    * is fully uninterruptible throughout
    */
  def uninterruptibleExcept[E, A](r: RestoreInterruption2[F] => F[E, A]): F[E, A]

  /** Like [[bracketCase]], but `acquire` can contain marked interruptible regions as in [[uninterruptibleExcept]] */
  def bracketExcept[E, A, B](acquire: RestoreInterruption2[F] => F[E, A])(release: (A, Exit[E, B]) => F[Nothing, Unit])(use: A => F[E, B]): F[E, B]

  @inline final def orTerminate[A](r: F[Throwable, A]): F[Nothing, A] = {
    catchAll(r)(terminate(_))
  }

  /** @note Will return either [[Exit.Error]] or [[Exit.Termination]]. [[Exit.Interruption]] cannot be sandboxed.
   *       Use [[guaranteeOnInterrupt]] for cleanups on interruptions. */
  @inline final def sandboxExit[E, A](r: F[E, A]): F[Nothing, Exit[E, A]] = {
    redeemPure(sandbox(r))(identity, Exit.Success(_))
  }
}

private[bio] sealed trait PanicSyntax
object PanicSyntax {
  implicit final class PanicOrTerminateK[F[+_, +_]](private val F: Panic2[F]) extends AnyVal {
    def orTerminateK[R]: F[Throwable, _] ~> F[Nothing, _] = {
      new (F[Throwable, _] ~> F[Nothing, _]) {
        override def apply[A](fa: F[Throwable, A]): F[Nothing, A] = F.orTerminate(fa)
      }
    }
  }
}
