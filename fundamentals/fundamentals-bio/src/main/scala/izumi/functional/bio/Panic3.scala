package izumi.functional.bio

import cats.~>
import izumi.functional.bio.data.RestoreInterruption3

trait Panic3[F[-_, +_, +_]] extends Bracket3[F] with PanicSyntax {
  def terminate(v: => Throwable): F[Any, Nothing, Nothing]

  def sandbox[R, E, A](r: F[R, E, A]): F[R, Exit.Failure[E], A]

  /**
    * Signal interruption to this fiber.
    *
    * This is _NOT_ the same as
    *
    * {{{
    *   F.halt(Exit.Interrupted(Trace.empty))
    * }}}
    *
    * The code above exits with `Exit.Interrupted` failure *unconditionally*,
    * whereas [[sendInterruptToSelf]] will not exit when in an uninterruptible
    * region. Example:
    *
    * {{{
    *   F.uninterruptible {
    *     F.halt(Exit.Interrupted(Trace.empty)) *>
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
  def sendInterruptToSelf: F[Any, Nothing, Unit]

  def uninterruptible[R, E, A](r: F[R, E, A]): F[R, E, A] = {
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
  def uninterruptibleExcept[R, E, A](r: RestoreInterruption3[F] => F[R, E, A]): F[R, E, A]

  /** Like [[bracketCase]], but `acquire` can contain marked interruptible regions as in [[uninterruptibleExcept]] */
  def bracketExcept[R, E, A, B](acquire: RestoreInterruption3[F] => F[R, E, A])(release: (A, Exit[E, B]) => F[R, Nothing, Unit])(use: A => F[R, E, B]): F[R, E, B]

  @inline final def orTerminate[R, A](r: F[R, Throwable, A]): F[R, Nothing, A] = {
    catchAll(r)(terminate(_))
  }
}

private[bio] sealed trait PanicSyntax
object PanicSyntax {
  implicit final class PanicOrTerminateK[F[-_, +_, +_]](private val F: Panic3[F]) extends AnyVal {
    def orTerminateK[R]: F[R, Throwable, _] ~> F[R, Nothing, _] = {
      Lambda[F[R, Throwable, _] ~> F[R, Nothing, _]](f => F.orTerminate(f))
    }
  }
}
