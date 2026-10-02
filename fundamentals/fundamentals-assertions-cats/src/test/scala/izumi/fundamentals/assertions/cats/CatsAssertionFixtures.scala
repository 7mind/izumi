package izumi.fundamentals.assertions.cats

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import izumi.fundamentals.assertions.{Assert, AssertionContext, AssertionFailure, Assertions, Evaluation, ObservationKind, ValueRenderer}
import izumi.fundamentals.assertions.cats.CatsAssertionSuspension.*

import java.util.concurrent.atomic.AtomicInteger

object CatsAssertionFixtures extends IOApp.Simple {
  override def run: IO[Unit] = IO.defer {
    var checks = 0
    def verify(condition: Boolean, message: String): Unit = {
      checks += 1
      if (!condition) throw new IllegalStateException(message)
    }
    def failure(result: Either[Throwable, Unit]): AssertionFailure = result match {
      case Left(value: AssertionFailure) => value
      case Left(other) => throw new IllegalStateException("Expected suspended assertion failure", other)
      case Right(_) => throw new IllegalStateException("Expected suspended assertion to fail")
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
    val success: IO[Unit] = receiver.assert1[IO]({ val _ = evaluations.incrementAndGet(); true }, context)
    val failures = new AtomicInteger(0)
    val failing: IO[Unit] = Assert.assert1[IO](failures.incrementAndGet() < 0)
    val branches = new AtomicInteger(0)
    val changing: IO[Unit] = Assert.assert1[IO](branches.incrementAndGet() > 1 && false)
    val original = new IllegalArgumentException("operand failure")
    def throwingCondition: Boolean = throw original
    val throwing = Assert.assert1[IO](throwingCondition)
    val contextException = new IllegalArgumentException("context failure")
    def throwingContext: AssertionContext = throw contextException
    val contextThrowing = Assert.assert1[IO](true, throwingContext)

    for {
      _ <- IO.delay(verify(receivers.get() == 1 && evaluations.get() == 0 && contexts.get() == 0 && failures.get() == 0 && branches.get() == 0, "Construction evaluates only the receiver"))
      _ <- success
      _ <- IO.delay(verify(evaluations.get() == 1 && contexts.get() == 1, "First execution evaluates condition and context"))
      _ <- success
      _ <- IO.delay(verify(receivers.get() == 1 && evaluations.get() == 2 && contexts.get() == 2, "Repeated execution creates a new check without reevaluating its receiver"))
      first <- failing.attempt
      second <- failing.attempt
      _ <- IO.delay {
        verify(failures.get() == 2 && (failure(first) ne failure(second)), "Repeated failures are distinct")
        verify(Vector(operand(failure(first)), operand(failure(second))) == Vector("1", "2"), "Repeated observations retain each execution's value")
      }
      concurrent <- (failing.attempt, failing.attempt).parTupled
      _ <- IO.delay {
        verify(failures.get() == 4 && (failure(concurrent._1) ne failure(concurrent._2)), "Concurrent failures are distinct")
        verify(Set(operand(failure(concurrent._1)), operand(failure(concurrent._2))) == Set("3", "4"), "Concurrent observations are independent")
        verify(operand(failure(first)) == "1", "Later executions never change earlier diagnostics")
      }
      skipped <- changing.attempt
      evaluated <- changing.attempt
      _ <- IO.delay {
        verify(failure(skipped).diagnostic.observations.exists(_.evaluation == Evaluation.NotEvaluated), "First execution preserves its skipped branch")
        verify(!failure(evaluated).diagnostic.observations.exists(_.evaluation == Evaluation.NotEvaluated), "Second execution has independent branch observations")
      }
      thrown <- throwing.attempt
      _ <- IO.delay(verify(thrown == Left(original), "Operand exception identity survives suspension"))
      contextThrown <- contextThrowing.attempt
      _ <- IO.delay(verify(contextThrown == Left(contextException), "Context exceptions are suspended"))
      _ <- IO.delay(println(s"CATS_ASSERTION_FIXTURES_OK checks=$checks"))
    } yield ()
  }
}
