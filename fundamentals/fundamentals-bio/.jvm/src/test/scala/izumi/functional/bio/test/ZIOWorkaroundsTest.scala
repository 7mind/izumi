package izumi.functional.bio.test

import izumi.functional.bio.{Async2, Exit, F}
import org.scalatest.wordspec.AnyWordSpec

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
            assert(exc.getMessage.contains("during a `parTraverse` or `zipWithPar` operation"))
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
            assert(exc.getMessage.contains("during a `parTraverse` or `zipWithPar` operation"))
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
            assert(exc.getMessage.contains("during a `parTraverse` or `zipWithPar` operation"))
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
            assert(exc.getMessage.contains("during a `parTraverse` or `zipWithPar` operation"))
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
            assert(!exc.getMessage.contains("during a `parTraverse` or `zipWithPar` operation"))
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
            assert(!exc.getMessage.contains("during a `parTraverse` or `zipWithPar` operation"))
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
            assert(exc.getMessage.contains("during a `parTraverse` or `zipWithPar` operation"))
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
            assert(exc.getMessage.contains("during a `parTraverse` or `zipWithPar` operation"))
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
            assert(exc.getMessage.contains("during a `parTraverse` or `zipWithPar` operation"))
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
            assert(exc.getMessage.contains("during a `parTraverse` or `zipWithPar` operation"))
          case other =>
            fail(s"Unexpected status: $other")
        }
    }

  }

}
