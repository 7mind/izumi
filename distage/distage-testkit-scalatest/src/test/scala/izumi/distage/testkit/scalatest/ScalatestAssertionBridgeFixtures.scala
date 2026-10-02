package izumi.distage.testkit.scalatest

import cats.effect.{IO, IOApp}
import izumi.fundamentals.assertions.{Assert, AssertionFailure}
import org.scalatest.{Args, Reporter}
import org.scalatest.events.{Event, TestFailed, TestSucceeded}

import java.util.concurrent.atomic.AtomicInteger

object ScalatestAssertionBridgeFixtures extends IOApp.Simple {
  private final class Events extends Reporter {
    private var events = Vector.empty[Event]
    override def apply(event: Event): Unit = synchronized { events :+= event }
    def snapshot: Vector[Event] = synchronized { events }
  }

  private final class BridgeSuite(evaluations: AtomicInteger) extends SpecIdentity {
    "new assertion" should {
      "display its portable failure" in {
        AssertionBridge(Assert.assert(evaluations.incrementAndGet() > 1))
      }
    }
  }

  override def run: IO[Unit] = IO.defer {
    val evaluations = new AtomicInteger(0)
    val events = new Events
    val suite = new BridgeSuite(evaluations)
    val status = suite.run(None, Args(events))
    IO.async_[Boolean] { callback => status.whenCompleted(result => callback(result.toEither)) }.flatMap { successful =>
      IO.delay {
        val failures = events.snapshot.collect { case failure: TestFailed => failure }
        if (evaluations.get() != 1 || failures.size != 1 || events.snapshot.exists(_.isInstanceOf[TestSucceeded])) {
          throw new IllegalStateException(s"Legacy distage runner must report one executed assertion failure: status=$successful evaluations=${evaluations.get()} failures=${failures.size} events=${events.snapshot.map(_.getClass.getSimpleName)}")
        }
        val failure = failures.head
        if (!failure.throwable.exists(_.isInstanceOf[AssertionFailure])) throw new IllegalStateException("Legacy reporter lost the portable assertion failure")
        if (!failure.message.contains("Assertion failed") || !failure.message.contains("ScalatestAssertionBridgeFixtures.scala")) {
          throw new IllegalStateException("Legacy reporter omitted the assertion diagnostic and source location")
        }
        println(s"SCALATEST_ASSERTION_BRIDGE_OK executed=1 failed=1 legacyStatus=$successful")
      }
    }
  }
}
