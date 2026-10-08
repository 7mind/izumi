package izumi.distage.testkit.protocol

import java.nio.file.{Files, Paths}
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

object ForkReceiptRegressionFixtures {
  private final val WaitSeconds = 10L
  private final val PollMillis = 1L

  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 2 && Set("unicode", "replacement").contains(arguments.head), "Expected regression mode and a new owned directory")
    val parent = Paths.get(arguments(1)).toAbsolutePath
    require(!Files.exists(parent), "Regression directory must be new")
    val _ = Files.createDirectories(parent)
    val store = FileForkReceiptStore.create(parent)
    val memory = ForkReceiptFixtures.memoryStore()
    try {
      if (arguments.head == "unicode") {
        unicode(memory, memory)
        unicode(store, FileForkReceiptStore.open(store.directory))
      } else {
        replacement(memory, memory)
        replacement(store, FileForkReceiptStore.open(store.directory))
      }
      println("FORK_RECEIPT_REGRESSION_OK " + arguments.head)
    } finally { memory.close(); store.close(); Files.delete(parent) }
  }

  private def unicode(store: ForkReceiptStore, reader: ForkReceiptReader): Unit = {
    val summary = ForkReceiptSummary(1, ForkReceiptCounts(1, 0, 0, 0, 0, 0, 0))
    val valid = ForkReceiptSuite("fixture.符号𝔘")
    store.publish(valid, summary)
    require(reader.received(valid).contains(summary), "Valid supplementary Unicode did not round-trip")
    Vector("\uD800", "\uDC00", "fixture.\uD800X").foreach { name =>
      var accepted = Option.empty[ForkReceiptSuite]
      try accepted = Some(ForkReceiptSuite(name)) catch { case _: IllegalArgumentException => () }
      accepted.foreach { suite =>
        store.publish(suite, summary)
        var failure = Option.empty[Throwable]
        try { val _ = reader.received(suite) } catch { case cause: IllegalArgumentException => failure = Some(cause) }
        println("FORK_RECEIPT_UNICODE_REPRO accepted=true roundTripFailure=" + failure)
      }
      require(accepted.isEmpty, "Malformed UTF-16 suite name was accepted")
    }
  }

  private def replacement(store: ForkReceiptStore, source: ForkReceiptReader): Unit = {
    val first = ForkReceiptSuite("fixture.FirstSuite")
    val second = ForkReceiptSuite("fixture.SecondSuite")
    val summary = ForkReceiptSummary(1, ForkReceiptCounts(1, 0, 0, 0, 0, 0, 0))
    store.publish(first, summary)
    val missingReads = new CountDownLatch(2)
    val finished = new CountDownLatch(1)
    val failure = new AtomicReference[Option[Throwable]](None)
    val reader = new ForkReceiptReader {
      override def received(suite: ForkReceiptSuite): Option[ForkReceiptSummary] = {
        val value = source.received(suite)
        if (suite == second && value.isEmpty) missingReads.countDown()
        value
      }
    }
    val target = new Thread(() => {
      try new ForkReceiptAwaiter(reader, ForkReceiptWaitPolicy(TimeUnit.SECONDS.toNanos(WaitSeconds), PollMillis)).await(Map(first -> summary, second -> summary))
      catch { case cause: Throwable => failure.set(Some(cause)) }
      finally finished.countDown()
    })
    target.start()
    try {
      require(missingReads.await(WaitSeconds, TimeUnit.SECONDS), "Waiter did not observe the missing second receipt")
      store.publish(first, summary.copy(counts = ForkReceiptCounts(0, 1, 0, 0, 0, 0, 0)))
      store.publish(second, summary)
      require(finished.await(WaitSeconds, TimeUnit.SECONDS), "Waiter did not finish after both receipts were published")
      println("FORK_RECEIPT_REPLACEMENT_REPRO failure=" + failure.get())
      require(failure.get().exists(cause => cause.isInstanceOf[IllegalArgumentException] && cause.getMessage.contains("Fork receipt differs")), "Waiter accepted a replaced earlier receipt")
    } finally { target.interrupt(); target.join(TimeUnit.SECONDS.toMillis(WaitSeconds)) }
    require(!target.isAlive, "Regression waiter survived its fixture")
  }
}
