package zio._izumicompat_

import zio.{Exit, Fiber, Trace, Unsafe, ZIO}
import zio.internal.FiberScope

/**
  * From interop-cats https://github.com/zio/interop-cats/blob/b120f74c36f7de40d425e715674b2f4279402627/zio-interop-cats/shared/src/main/scala/zio/interop/cats.scala#L372
  * compatibility with `race` without the "supervision" semantics that break uninterruptible races.
  */
object __ZIORaceCompat {

  /**
    * An implementation of `raceFirst` that forks the left and right fibers in
    * the global scope instead of the scope of the parent fiber.
    */
  final def raceFirst[R, E, A](self: ZIO[R, E, A], that: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    this.race(self.exit, that.exit).flatMap(ZIO.done(_))

  /**
    * An implementation of `race` that forks the left and right fibers in
    * the global scope instead of the scope of the parent fiber.
    */
  private def race[R, E, A](self: ZIO[R, E, A], that: => ZIO[R, E, A])(implicit trace: Trace): ZIO[R, E, A] =
    ZIO.fiberIdWith {
      parentFiberId =>
        this.raceWith(self, that)(
          (exit, right) =>
            exit.foldExitZIO[Any, E, A](
              cause => right.join.mapErrorCause(cause && _),
              a => right.interruptAs(parentFiberId).as(a),
            ),
          (exit, left) =>
            exit.foldExitZIO[Any, E, A](
              cause => left.join.mapErrorCause(_ && cause),
              a => left.interruptAs(parentFiberId).as(a),
            ),
        )
    }

  /**
    * An implementation of `raceWith` that forks the left and right fibers in
    * the global scope instead of the scope of the parent fiber.
    */
  final def raceWith[R, E, E2, E3, A, B, C](
    left: ZIO[R, E, A],
    right: => ZIO[R, E2, B],
  )(leftDone: (Exit[E, A], Fiber[E2, B]) => ZIO[R, E3, C],
    rightDone: (Exit[E2, B], Fiber[E, A]) => ZIO[R, E3, C],
  )(implicit trace: Trace
  ): ZIO[R, E3, C] =
    this.raceFibersWith(left, right)(
      (winner, loser) =>
        winner.await.flatMap {
          case exit: Exit.Success[A] =>
            winner.inheritAll.flatMap(_ => leftDone(exit, loser))
          case exit: Exit.Failure[E] =>
            leftDone(exit, loser)
        },
      (winner, loser) =>
        winner.await.flatMap {
          case exit: Exit.Success[B] =>
            winner.inheritAll.flatMap(_ => rightDone(exit, loser))
          case exit: Exit.Failure[E2] =>
            rightDone(exit, loser)
        },
    )

  /**
    * An implementation of `raceFibersWith` that forks the left and right fibers
    * in the global scope instead of the scope of the parent fiber.
    */
  private final def raceFibersWith[R, E, E2, E3, A, B, C](
    left: ZIO[R, E, A],
    right: ZIO[R, E2, B],
  )(leftWins: (Fiber.Runtime[E, A], Fiber.Runtime[E2, B]) => ZIO[R, E3, C],
    rightWins: (Fiber.Runtime[E2, B], Fiber.Runtime[E, A]) => ZIO[R, E3, C],
  )(implicit trace: Trace
  ): ZIO[R, E3, C] =
    ZIO.withFiberRuntime[R, E3, C] {
      (parentFiber, parentStatus) =>
        import java.util.concurrent.atomic.AtomicBoolean

        val parentRuntimeFlags = parentStatus.runtimeFlags

        @inline def complete[EE, EE2, AA, BB](
          winner: Fiber.Runtime[EE, AA],
          loser: Fiber.Runtime[EE2, BB],
          cont: (Fiber.Runtime[EE, AA], Fiber.Runtime[EE2, BB]) => ZIO[R, E3, C],
          ab: AtomicBoolean,
          cb: ZIO[R, E3, C] => Any,
        ): Any =
          if (ab.compareAndSet(true, false)) {
            cb(cont(winner, loser))
          }

        val raceIndicator = new AtomicBoolean(true)

        val leftFiber =
          ZIO.unsafe.makeChildFiber(trace, left, parentFiber, parentRuntimeFlags, FiberScope.global)(Unsafe.unsafe)
        val rightFiber =
          ZIO.unsafe.makeChildFiber(trace, right, parentFiber, parentRuntimeFlags, FiberScope.global)(Unsafe.unsafe)

        val startLeftFiber = leftFiber.startSuspended()(Unsafe.unsafe)
        val startRightFiber = rightFiber.startSuspended()(Unsafe.unsafe)

        ZIO
          .async[R, E3, C](
            {
              cb =>
                leftFiber.addObserver {
                  _ =>
                    complete(leftFiber, rightFiber, leftWins, raceIndicator, cb)
                    ()
                }(Unsafe.unsafe)

                rightFiber.addObserver {
                  _ =>
                    complete(rightFiber, leftFiber, rightWins, raceIndicator, cb)
                    ()
                }(Unsafe.unsafe)

                startLeftFiber(left)
                startRightFiber(right)
                ()
            },
            leftFiber.id <> rightFiber.id,
          )
    }

}
