package izumi.distage.testkit.runner

import izumi.distage.testkit.protocol.*
import izumi.distage.testkit.runner.spec.AnyWordSpec

import java.util.concurrent.{Callable, CountDownLatch, Executors, TimeUnit}
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*

object ApplicationOutputBlockingFixtures {
  private final val WorkerThreads = 2
  private final val TimeoutSeconds = 30
  private final val CancellationSeconds = 1

  def main(args: Array[String]): Unit = {
    val workers = Executors.newFixedThreadPool(WorkerThreads)
    val entered = new CountDownLatch(1)
    val release = new CountDownLatch(1)
    val cancellationReturned = new CountDownLatch(1)
    val cancellationEntered = new CountDownLatch(1)
    val inline = new ExecutionContext {
      override def execute(runnable: Runnable): Unit = runnable.run()
      override def reportFailure(cause: Throwable): Unit = throw cause
    }
    val identity = CatalogueIdentity(BuildId("blocking-application"), BuildTargetId("blocking-target"), CatalogueId("blocking-catalogue"))
    val run = RunId("inline-blocking-output")
    var messages = Vector.empty[ProtocolMessage]
    val output = new ProtocolOutput {
      override def accept(message: ProtocolMessage): Unit = synchronized {
        message match {
          case ProtocolMessage.Event(_, _: RunEvent.Started) =>
            entered.countDown()
            require(release.await(TimeoutSeconds, TimeUnit.SECONDS), "Output release gate timed out")
          case _ => ()
        }
        messages :+= message
      }
    }
    val application = new TestApplication(run, identity, Vector(() => new AnyWordSpec {
      "body after output" in ()
    }), inline, output)
    val request = RunRequest(identity, Selection.All, RunOverrides(Vector.empty, Vector.empty, MemoizationOverride.Inherit))
    try {
      val execution = workers.submit(new Callable[Unit] {
        override def call(): Unit = Await.result(application.accept(ProtocolMessage.Request(RequestOperation.Execute, run, request)), TimeoutSeconds.seconds)
      })
      require(entered.await(TimeoutSeconds, TimeUnit.SECONDS), "Output callback did not enter")
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
        println("APPLICATION_OUTPUT_BLOCKING_ADMISSION cancellationEntered=true returned=" + returned + " outputHeld=" + (release.getCount == 1))
        require(returned, "Cancellation must return while a protocol output callback remains held")
      }
      finally release.countDown()
      execution.get(TimeoutSeconds, TimeUnit.SECONDS)
      cancellation.get(TimeoutSeconds, TimeUnit.SECONDS)
      val outcomes = output.synchronized(messages.collect { case ProtocolMessage.Completed(outcome) => outcome })
      require(outcomes.size == 1 && outcomes.head.cancelled && !outcomes.head.successful, "Cancellation during held output must reach its terminal outcome")
      println("APPLICATION_OUTPUT_BLOCKING_CANCELLATION_OK admission=immediate completion=after_output")
    } finally {
      release.countDown()
      workers.shutdown()
      require(workers.awaitTermination(TimeoutSeconds, TimeUnit.SECONDS), "Output blocking fixture workers did not terminate")
      println("APPLICATION_OUTPUT_BLOCKING_EXECUTOR_TERMINATED")
    }
  }
}
