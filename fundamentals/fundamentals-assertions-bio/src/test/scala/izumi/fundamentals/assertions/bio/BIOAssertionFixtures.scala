package izumi.fundamentals.assertions.bio

import izumi.fundamentals.assertions.{Assert, AssertionContext, AssertionFailure, Assertions, Evaluation, ObservationKind, ValueRenderer}
import izumi.fundamentals.assertions.bio.BIOAssertionSuspension.*
import zio.{Exit, ZIO, ZIOAppDefault}

import java.util.concurrent.atomic.AtomicInteger

object BIOAssertionFixtures extends ZIOAppDefault {
  override def run: ZIO[Any, Nothing, Unit] = ZIO.suspendSucceed {
    var checks = 0
    def verify(condition: Boolean, message: String): Unit = {
      checks += 1
      if (!condition) throw new IllegalStateException(message)
    }
    def failure(result: Exit[Nothing, Unit]): AssertionFailure = result match {
      case Exit.Failure(cause) =>
        if (cause.failureOption.nonEmpty) throw new IllegalStateException("Assertion changed the typed error channel")
        cause.dieOption match {
          case Some(value: AssertionFailure) => value
          case Some(other) => throw new IllegalStateException("Expected assertion defect", other)
          case None => throw new IllegalStateException("Expected assertion defect")
        }
      case Exit.Success(_) => throw new IllegalStateException("Expected suspended assertion to fail")
    }
    def operand(value: AssertionFailure): String = value.diagnostic.observations.find(_.site.kind == ObservationKind.Operand).get.evaluation match {
      case Evaluation.Evaluated(captured) => captured.render(ValueRenderer.standard)
      case Evaluation.NotEvaluated => throw new IllegalStateException("Expected evaluated counter operand")
    }

    val evaluations = new AtomicInteger(0)
    val contexts = new AtomicInteger(0)
    val receivers = new AtomicInteger(0)
    lazy val receiver: Assertions = { val _ = receivers.incrementAndGet(); new Assertions {} }
    def context: AssertionContext = { val _ = contexts.incrementAndGet(); AssertionContext.standard }
    val success: ZIO[Any, Nothing, Unit] = receiver.assert2[zio.IO]({ val _ = evaluations.incrementAndGet(); true }, context)
    val failures = new AtomicInteger(0)
    val failing: ZIO[Any, Nothing, Unit] = Assert.assert2[zio.IO](failures.incrementAndGet() < 0)
    val branches = new AtomicInteger(0)
    val changing: ZIO[Any, Nothing, Unit] = Assert.assert2[zio.IO](branches.incrementAndGet() > 1 && false)
    val original = new IllegalArgumentException("operand failure")
    def throwingCondition: Boolean = throw original
    val throwing = Assert.assert2[zio.IO](throwingCondition)
    val contextException = new IllegalArgumentException("context failure")
    def throwingContext: AssertionContext = throw contextException
    val contextThrowing = Assert.assert2[zio.IO](true, throwingContext)
    val typed: ZIO[Any, String, Unit] = ZIO.fail("typed failure") *> success

    for {
      _ <- ZIO.succeed(verify(receivers.get() == 1 && evaluations.get() == 0 && contexts.get() == 0 && failures.get() == 0 && branches.get() == 0, "Construction evaluates only the receiver"))
      _ <- success
      _ <- ZIO.succeed(verify(evaluations.get() == 1 && contexts.get() == 1, "First execution evaluates condition and context"))
      _ <- success
      _ <- ZIO.succeed(verify(receivers.get() == 1 && evaluations.get() == 2 && contexts.get() == 2, "Repeated execution creates a new check without reevaluating its receiver"))
      first <- failing.exit
      second <- failing.exit
      _ <- ZIO.succeed {
        verify(failures.get() == 2 && (failure(first) ne failure(second)), "Repeated failures are distinct defects")
        verify(Vector(operand(failure(first)), operand(failure(second))) == Vector("1", "2"), "Repeated observations retain each execution's value")
      }
      concurrent <- failing.exit.zipPar(failing.exit)
      _ <- ZIO.succeed {
        verify(failures.get() == 4 && (failure(concurrent._1) ne failure(concurrent._2)), "Concurrent failures are distinct defects")
        verify(Set(operand(failure(concurrent._1)), operand(failure(concurrent._2))) == Set("3", "4"), "Concurrent observations are independent")
        verify(operand(failure(first)) == "1", "Later executions never change earlier diagnostics")
      }
      skipped <- changing.exit
      evaluated <- changing.exit
      _ <- ZIO.succeed {
        verify(failure(skipped).diagnostic.observations.exists(_.evaluation == Evaluation.NotEvaluated), "First execution preserves its skipped branch")
        verify(!failure(evaluated).diagnostic.observations.exists(_.evaluation == Evaluation.NotEvaluated), "Second execution has independent branch observations")
      }
      thrown <- throwing.exit
      _ <- ZIO.succeed(verify(thrown.causeOption.exists(c => c.failureOption.isEmpty && c.dieOption.contains(original)), "Operand exception identity survives as a defect"))
      contextThrown <- contextThrowing.exit
      _ <- ZIO.succeed(verify(contextThrown.causeOption.exists(c => c.failureOption.isEmpty && c.dieOption.contains(contextException)), "Context exceptions are suspended defects"))
      typedFailure <- typed.exit
      _ <- ZIO.succeed(verify(typedFailure.causeOption.exists(_.failureOption.contains("typed failure")) && evaluations.get() == 2, "Existing typed failures remain unchanged"))
      _ <- ZIO.succeed(println(s"BIO_ASSERTION_FIXTURES_OK checks=$checks"))
    } yield ()
  }
}
