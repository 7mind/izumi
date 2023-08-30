package izumi.functional.bio.test

import izumi.functional.bio.*
import org.scalatest.Assertion
import org.scalatest.wordspec.AnyWordSpec
import zio.ZIO

class ZIOWorkaroundsTest extends AnyWordSpec {

  val runtime = UnsafeRun2.createZIO()

  "Issue https://github.com/zio/zio/issues/6911" should {

    "not reproduce with F.parTraverse" in runtime.unsafeRun {
      F.parTraverse(
        List(
          F.unit.forever,
          F.terminate(new RuntimeException("testexception")),
        )
      )(identity).sandboxExit.map {
          case Exit.Termination(exc, _, _) =>
            assert(exc.getMessage.contains("testexception"))
          case other =>
            fail(s"Unexpected status: $other")
        }
    }

    "not reproduce with F.parTraverse_" in runtime.unsafeRun {
      F.parTraverse_(
        List(
          F.unit.forever,
          F.terminate(new RuntimeException("testexception")),
        )
      )(identity).sandboxExit.map {
          case Exit.Termination(exc, _, _) =>
            assert(exc.getMessage.contains("testexception"))
          case other =>
            fail(s"Unexpected status: $other")
        }
    }

    "not reproduce with F.parTraverseN" in runtime.unsafeRun {
      F.parTraverseN(2)(
        List(
          F.unit.forever,
          F.terminate(new RuntimeException("testexception")),
        )
      )(identity).sandboxExit.map {
          case Exit.Termination(exc, _, _) =>
            assert(exc.getMessage.contains("testexception"))
          case other =>
            fail(s"Unexpected status: $other")
        }
    }

    "not reproduce with F.parTraverseN_" in runtime.unsafeRun {
      F.parTraverseN_(2)(
        List(
          F.unit.forever,
          F.terminate(new RuntimeException("testexception")),
        )
      )(identity).sandboxExit.map {
          case Exit.Termination(exc, _, _) =>
            assert(exc.getMessage.contains("testexception"))
          case other =>
            fail(s"Unexpected status: $other")
        }
    }

    "not reproduce with F.race" in runtime.unsafeRun {
      F.race(
        F.unit.forever,
        F.terminate(new RuntimeException("testexception")),
      ).sandboxExit.map {
          case Exit.Termination(exc, _, _) =>
            assert(exc.getMessage.contains("testexception"))
            assert(!exc.getMessage.contains("interrupt"))
          case other =>
            fail(s"Unexpected status: $other")
        }
    }

    "not reproduce with F.racePairUnsafe" in runtime.unsafeRun {
      F.racePairUnsafe(
        F.unit.forever,
        F.terminate(new RuntimeException("testexception")),
      ).map {
          case Right((_, Exit.Termination(exc, _, _))) =>
            assert(exc.getMessage.contains("testexception"))
          case other =>
            fail(s"Unexpected status: $other")
        }
    }

    "not reproduce with F.zipWithPar" in runtime.unsafeRun {
      F.zipWithPar(
        F.unit.forever.widen[Unit],
        F.terminate(new RuntimeException("testexception")).widen[Unit],
      )((_, _) => ()).sandboxExit.map {
          case Exit.Termination(exc, _, _) =>
            assert(exc.getMessage.contains("testexception"))
          case other =>
            fail(s"Unexpected status: $other")
        }
    }

    "not reproduce with F.zipPar" in runtime.unsafeRun {
      F.zipPar(
        F.unit.forever,
        F.terminate(new RuntimeException("testexception")),
      ).sandboxExit.map {
          case Exit.Termination(exc, _, _) =>
            assert(exc.getMessage.contains("testexception"))
          case other =>
            fail(s"Unexpected status: $other")
        }
    }

    "not reproduce with F.zipParLeft" in runtime.unsafeRun {
      F.zipParLeft(
        F.unit.forever,
        F.terminate(new RuntimeException("testexception")),
      ).sandboxExit.map {
          case Exit.Termination(exc, _, _) =>
            assert(exc.getMessage.contains("testexception"))
          case other =>
            fail(s"Unexpected status: $other")
        }
    }

    "not reproduce with F.zipParRight" in runtime.unsafeRun {
      F.zipParRight(
        F.unit.forever,
        F.terminate(new RuntimeException("testexception")),
      ).sandboxExit.map {
          case Exit.Termination(exc, _, _) =>
            assert(exc.getMessage.contains("testexception"))
          case other =>
            fail(s"Unexpected status: $other")
        }
    }

    "guaranteeExceptOnInterrupt works correctly" in runtime.unsafeRun {
      for {
        succRes <- F.mkRef(Option.empty[Boolean])
        failRes <- F.mkRef(Option.empty[Boolean])
        terminateRes <- F.mkRef(Option.empty[Boolean])
        innerInterruptRes <- F.mkRef(Option.empty[Boolean])
        parTraverseRes <- F.mkRef(Option.empty[Boolean])
        outerInterruptRes1 <- F.mkRef(Option.empty[Boolean])
        outerInterruptRes2 <- F.mkRef(Option.empty[Boolean])

        _ <- F.pure("x").guaranteeExceptOnInterrupt(_ => succRes.set(Some(true))).sandboxExit
        _ <- F.fail("x").guaranteeExceptOnInterrupt(_ => failRes.set(Some(true))).sandboxExit
        _ <- F.terminate(new RuntimeException("x")).guaranteeExceptOnInterrupt(_ => terminateRes.set(Some(true))).sandboxExit
        _ <- ZIO.interrupt.guaranteeExceptOnInterrupt(_ => innerInterruptRes.set(Some(true))).sandboxExit
        l <- F.mkLatch
        _ <-
          F.parTraverse_(
            List(
              F.parTraverse(
                List(
                  F.unit.forever,
                  l.await *> F.terminate(new RuntimeException("testexception")),
                )
              )(identity).guaranteeExceptOnInterrupt(_ => parTraverseRes.set(Some(true))),
              (l.succeed(()) *> ZIO.unit.forever)
                .guaranteeExceptOnInterrupt(_ => outerInterruptRes1.set(Some(true))).guaranteeOnInterrupt(_ => outerInterruptRes2.set(Some(true))),
            )
          )(identity).sandboxExit

        results <- F.traverse(List(succRes, failRes, terminateRes, innerInterruptRes, parTraverseRes, outerInterruptRes1, outerInterruptRes2))(_.get)
      } yield assert(results == List(Some(true), Some(true), Some(true), Some(true), Some(true), None, Some(true)))
    }

  }

  "Issue https://github.com/zio/zio/issues/8243 leaking 'interruption inheritance' into BIO" should {
    // related issues https://github.com/zio/zio/issues/5459 https://github.com/zio/zio/issues/3100 https://github.com/zio/zio/issues/3065 https://github.com/zio/zio/issues/1764

    "F.timeout in uninterruptible region is correctly not interrupted when parent is interrupted" in {
      import scala.concurrent.duration.*

      def test[F[+_, +_]: Async2: Temporal2: Primitives2: Fork2]: F[Nothing, Assertion] = {
        for {
          fiberStarted <- F.mkLatch
          stopFiber <- F.mkLatch
          innerFiberNotInterrupted <- F.mkLatch
          outerFiberNotInterrupted <- F.mkLatch
          fiberNotInterrupted1 <- F.mkRef(false)
          fiberNotInterrupted2 <- F.mkRef(Option.empty[Boolean])
          fiber <- F.fork {
            F.uninterruptible(
              F.timeout(5.hours)(
                (fiberStarted.succeed(()) *>
                F.sleep(1.second) *>
                stopFiber.await *>
                innerFiberNotInterrupted.succeed(()))
                  .guaranteeExceptOnInterrupt(_ => fiberNotInterrupted1.set(true))
              ).flatMap {
                  promiseModifiedOrTimedOut =>
                    fiberNotInterrupted2.set(promiseModifiedOrTimedOut) *>
                    outerFiberNotInterrupted.succeed(())
                }
            )
          }
          _ <- fiberStarted.await
          innerIsNotInterrupted <- fiber.interrupt
            .as(false)
            .race(
              F.sleep(1.second) *>
              stopFiber.succeed(()) *>
              innerFiberNotInterrupted.await *>
              outerFiberNotInterrupted.await.as(true)
            )
          isNotInterrupted1 <- fiberNotInterrupted1.get
          isNotInterrupted2 <- fiberNotInterrupted2.get
        } yield assert(innerIsNotInterrupted && isNotInterrupted1 && (isNotInterrupted2 == Some(true)))
      }

      runtime.unsafeRun(test[zio.IO])
    }

    "F.race in uninterruptible region is correctly not interrupted when parent is interrupted" in {
      import scala.concurrent.duration.*

      def test[F[+_, +_]: Async2: Temporal2: Primitives2: Fork2]: F[Nothing, Assertion] = {
        for {
          fiberStarted <- F.mkLatch
          stopFiber <- F.mkLatch
          innerFiberNotInterrupted <- F.mkLatch
          outerFiberNotInterrupted <- F.mkLatch
          fiberNotInterrupted1 <- F.mkRef(false)
          fiberNotInterrupted2 <- F.mkRef(Option.empty[Boolean])
          fiber <- F.fork {
            F.uninterruptible(
              F.race(
                r1 = (
                  fiberStarted.succeed(()) *>
                    F.sleep(1.second) *>
                    stopFiber.await *>
                    innerFiberNotInterrupted
                      .succeed(())
                      .map(Some(_))
                ).guaranteeExceptOnInterrupt(_ => fiberNotInterrupted1.set(true)),
                r2 = F.sleep(5.hours).as(None),
              ).flatMap {
                  promiseModifiedOrTimedOut =>
                    fiberNotInterrupted2.set(promiseModifiedOrTimedOut) *>
                    outerFiberNotInterrupted.succeed(())
                }
            )
          }
          _ <- fiberStarted.await
          innerIsNotInterrupted <- fiber.interrupt
            .as(false)
            .race(
              F.sleep(1.second) *>
              stopFiber.succeed(()) *>
              innerFiberNotInterrupted.await *>
              outerFiberNotInterrupted.await.as(true)
            )
          isNotInterrupted1 <- fiberNotInterrupted1.get
          isNotInterrupted2 <- fiberNotInterrupted2.get
        } yield assert(innerIsNotInterrupted && isNotInterrupted1 && (isNotInterrupted2 == Some(true)))
      }

      runtime.unsafeRun(test[zio.IO])
    }

  }

  "avoid sleep/race 'interruption inheritance' in ZIO" should {

    "F.timeout interrupts the timed action correctly within an uninterruptible region" in {
      import scala.concurrent.duration.*

      def test[F[+_, +_]: Async2: Temporal2: Primitives2: Fork2]: F[String, Assertion] = {
        for {
          fiber <- F.fork {
            F.uninterruptible(
              F.timeout(1.seconds)(F.never)
            )
          }
          res <- fiber.join.timeoutFail("timed out")(1.minute)
        } yield assert(res.isEmpty)
      }

      runtime.unsafeRun(test[zio.IO])
    }

    "F.race interrupts the timed action correctly within an uninterruptible region" in {
      import scala.concurrent.duration.*

      def test[F[+_, +_]: Async2: Temporal2: Primitives2: Fork2]: F[String, Assertion] = {
        for {
          started <- F.mkLatch
          finish <- F.mkLatch
          fiber <- F.fork {
            F.uninterruptible(
              F.race(started.succeed(()) *> finish.await.as(1), F.never)
            )
          }
          _ <- started.await
          _ <- finish.succeed(())
          res <- fiber.join.timeoutFail("timed out")(1.minute)
        } yield assert(res == 1)
      }

      runtime.unsafeRun(test[zio.IO])
    }

  }

}
