package izumi.functional.bio.test

import izumi.functional.bio.{Async2, Exit, F}
import org.scalatest.wordspec.AnyWordSpec
import zio.ZIO

class ZIOWorkaroundsTest extends AnyWordSpec {

  "Issue https://github.com/zio/zio/issues/6911" should {

    val silentRuntime = zio.Runtime.default.withReportFailure(_ => ())

    "not reproduce with F.parTraverse" in silentRuntime.unsafeRun {
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

    "not reproduce with F.parTraverse_" in silentRuntime.unsafeRun {
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

    "not reproduce with F.parTraverseN" in silentRuntime.unsafeRun {
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

    "not reproduce with F.parTraverseN_" in silentRuntime.unsafeRun {
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

    "not reproduce with F.race" in silentRuntime.unsafeRun {
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

    "not reproduce with F.racePairUnsafe" in silentRuntime.unsafeRun {
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

    "not reproduce with F.zipWithPar" in silentRuntime.unsafeRun {
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

    "not reproduce with F.zipPar" in silentRuntime.unsafeRun {
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

    "not reproduce with F.zipParLeft" in silentRuntime.unsafeRun {
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

    "not reproduce with F.zipParRight" in silentRuntime.unsafeRun {
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

    "guaranteeExceptOnInterrupt works correctly" in silentRuntime.unsafeRun {
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
        _ <-
          F.parTraverse_(
            List(
              F.parTraverse(
                List(
                  F.unit.forever,
                  F.terminate(new RuntimeException("testexception")),
                )
              )(identity).guaranteeExceptOnInterrupt(_ => parTraverseRes.set(Some(true))),
              ZIO.unit.forever.guaranteeExceptOnInterrupt(_ => outerInterruptRes1.set(Some(true))).guaranteeOnInterrupt(_ => outerInterruptRes2.set(Some(true))),
            )
          )(identity).sandboxExit

        results <- F.traverse(List(succRes, failRes, terminateRes, innerInterruptRes, parTraverseRes, outerInterruptRes1, outerInterruptRes2))(_.get)
      } yield assert(results == List(Some(true), Some(true), Some(true), Some(true), Some(true), None, Some(true)))
    }

  }

}
