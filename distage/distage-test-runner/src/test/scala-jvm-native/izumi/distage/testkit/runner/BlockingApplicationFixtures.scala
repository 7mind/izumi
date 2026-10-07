package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*

import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import scala.concurrent.Await
import scala.concurrent.duration.*

private[runner] object BlockingApplicationFixtures {
  private final val WorkerThreads = 2
  private final val TimeoutSeconds = 30
  private final val CancellationSeconds = 1

  final class Gate {
    private val entered = new CountDownLatch(1)
    private val release = new CountDownLatch(1)
    def hold(): Unit = {
      entered.countDown()
      require(release.await(TimeoutSeconds, TimeUnit.SECONDS), "Blocking application release gate timed out")
    }
    def awaitEntry(): Unit = require(entered.await(TimeoutSeconds, TimeUnit.SECONDS), "Blocking application did not enter")
    def held: Boolean = release.getCount == 1
    def open(): Unit = release.countDown()
  }

  def run(label: String, phase: String)(create: (Gate, FixtureSupport.RecordingOutput) => (() => TestSuite, ProtocolOutput)): Unit = {
    val workers = Executors.newFixedThreadPool(WorkerThreads)
    val gate = new Gate
    val cancellationReturned = new CountDownLatch(1)
    val cancellationEntered = new CountDownLatch(1)
    val identity = CatalogueIdentity(BuildId("blocking-application"), BuildTargetId("blocking-target"), CatalogueId("blocking-catalogue"))
    val run = RunId("inline-blocking-" + phase)
    val recorded = new FixtureSupport.RecordingOutput
    val (factory, output) = create(gate, recorded)
    val application = new TestApplication(run, identity, Vector(factory), FixtureSupport.inlineContext(), output)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    try {
      val execution = workers.submit(new Callable[Unit] {
        override def call(): Unit = Await.result(application.accept(ProtocolMessage.Request(RequestOperation.Execute, run, request)), TimeoutSeconds.seconds)
      })
      gate.awaitEntry()
      val cancellation = workers.submit(new Callable[Unit] {
        override def call(): Unit = {
          cancellationEntered.countDown()
          Await.result(application.accept(ProtocolMessage.Cancel(run)), TimeoutSeconds.seconds)
          cancellationReturned.countDown()
        }
      })
      require(cancellationEntered.await(TimeoutSeconds, TimeUnit.SECONDS), "Cancellation worker did not start")
      try {
        val returned = cancellationReturned.await(CancellationSeconds, TimeUnit.SECONDS)
        println(label + "_ADMISSION cancellationEntered=true returned=" + returned + " " + phase + "Held=" + gate.held)
        require(returned, "Cancellation must return while the application " + phase + " remains held")
      } finally gate.open()
      execution.get(TimeoutSeconds, TimeUnit.SECONDS)
      cancellation.get(TimeoutSeconds, TimeUnit.SECONDS)
      val outcomes = recorded.messages.collect { case ProtocolMessage.Completed(outcome) => outcome }
      require(outcomes.size == 1 && outcomes.head.cancelled && !outcomes.head.successful, "Cancellation during held " + phase + " must reach its terminal outcome")
      println(label + "_CANCELLATION_OK admission=immediate completion=after_" + phase)
    } finally {
      gate.open()
      workers.shutdown()
      require(workers.awaitTermination(TimeoutSeconds, TimeUnit.SECONDS), "Blocking application fixture workers did not terminate")
      println(label + "_EXECUTOR_TERMINATED")
    }
  }
}
