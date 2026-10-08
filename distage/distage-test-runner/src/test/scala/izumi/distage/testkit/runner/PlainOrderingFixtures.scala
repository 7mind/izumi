package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.spec.AnyWordSpec

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Promise}

object PlainOrderingFixtures {
  private final val MaximumCallbacks = 10000

  private final class ControlledContext extends ExecutionContext {
    private val ready = mutable.Queue.empty[Runnable]
    override def execute(runnable: Runnable): Unit = ready.enqueue(runnable)
    override def reportFailure(cause: Throwable): Unit = throw new IllegalStateException("Ordering fixture callback failed", cause)
    def drain(): Unit = {
      var callbacks = 0
      while (ready.nonEmpty) {
        callbacks += 1
        require(callbacks <= MaximumCallbacks, "Ordering fixture did not become idle")
        ready.dequeue().run()
      }
    }
  }

  private final class Marks {
    val release = Promise[Unit]()
    var firstStarted = false
    var firstFinished = false
    var secondStarted = false
  }

  private final class OrderedSuite(marks: Marks) extends AnyWordSpec {
    "first" in {
      marks.firstStarted = true
      marks.release.future.map { _ => marks.firstFinished = true }(sessionExecutionContext)
    }
    "second" in {
      marks.secondStarted = true
      require(marks.firstFinished, "Second plain body started before the first completed")
    }
  }

  private final class IndependentSuite(mark: () => Unit) extends AnyWordSpec {
    "independent" in mark()
  }

  def main(args: Array[String]): Unit = {
    val context = new ControlledContext
    val marks = new Marks
    var events = Vector.empty[RunEvent]
    val sink = new EventSink { override def accept(event: ProtocolMessage.Event): Unit = { events :+= event.event } }
    val identity = CatalogueIdentity(BuildId("plain-ordering"), BuildTargetId("controlled"), CatalogueId("default"))
    val session = new RunSession(identity, Vector(() => new OrderedSuite(marks)), context, sink)
    val _ = session.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    val outcome = session.execute(RunId("ordered"), request)
    context.drain()
    val firstPending = marks.firstStarted && !marks.firstFinished
    val secondStayedPending = !marks.secondStarted
    val startsBeforeRelease = events.collect { case started: RunEvent.TestStarted => started }.size
    val _ = marks.release.success(())
    context.drain()
    val result = outcome.value.getOrElse(throw new IllegalStateException("Ordering session did not finish")).get
    require(firstPending, "First plain body must remain pending until explicitly released")
    require(secondStayedPending && startsBeforeRelease == 1, "Default plain suite started a later body before the first completed")
    require(result.successful && result.results.size == 2 && marks.secondStarted, "Ordered plain bodies must both finish successfully")
    val separateContext = new ControlledContext
    val separateMarks = new Marks
    var independentStarted = false
    val separateSink = FixtureSupport.silentSink()
    val separateSession = new RunSession(identity, Vector(() => new OrderedSuite(separateMarks), () => new IndependentSuite(() => independentStarted = true)), separateContext, separateSink)
    val _ = separateSession.discover().fold(failure => throw new IllegalStateException(failure.message), value => value)
    val separateOutcome = separateSession.execute(RunId("separate-suites"), request)
    separateContext.drain()
    val overlapped = separateMarks.firstStarted && !separateMarks.firstFinished && independentStarted && !separateMarks.secondStarted
    val _ = separateMarks.release.success(())
    separateContext.drain()
    val separateResult = separateOutcome.value.getOrElse(throw new IllegalStateException("Separate suites did not finish")).get
    require(overlapped, "Independent plain suites must execute while another suite has a pending body")
    require(separateResult.successful && separateResult.results.size == 3, "Independent suites must retain every ordered result")
    println("PLAIN_ORDERING_FIXTURES_OK cases=5 order=registered same-suite=serial different-suites=overlap events=ordered")
  }
}
