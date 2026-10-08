package izumi.distage.testkit.protocol

import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

object ForkReceiptFixtures {
  private final val WaitSeconds = 10L
  private final val PollMillis = 1L
  private val suite = ForkReceiptSuite("fixture.SelectedSuite")
  private val counts = ForkReceiptCounts(3, 1, 1, 1, 1, 1, 1)
  private val summary = ForkReceiptSummary(1, counts)

  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Expected an owned fixture directory")
    val directory = Paths.get(arguments.head).toAbsolutePath
    require(!Files.exists(directory), "Fixture directory must be new")
    val _ = Files.createDirectories(directory)
    try {
      contract("memory", () => {
        val store = memoryStore()
        ContractViews(store, store)
      })
      contract("filesystem", () => {
        val store = FileForkReceiptStore.create(directory)
        ContractViews(store, FileForkReceiptStore.open(store.directory))
      })
      val first = FileForkReceiptStore.create(directory)
      val second = FileForkReceiptStore.create(directory)
      require(first.directory != second.directory, "Two commands reused a fork receipt directory")
      first.publish(suite, summary)
      val targetView = FileForkReceiptStore.open(first.directory)
      require(targetView.received(suite).contains(summary), "Target and host views disagree")
      require(second.received(suite).isEmpty, "A later command saw an earlier receipt")
      first.close()
      rejects(classOf[IllegalArgumentException]) { val _ = targetView.received(suite); () }
      require(!Files.exists(first.directory) && Files.isDirectory(second.directory), "Cleanup affected another generation")
      second.close()
      val malformed = FileForkReceiptStore.create(directory)
      try {
        malformed.publish(suite, summary)
        val entries = Files.list(malformed.directory)
        val receipt = try entries.findFirst().get() finally entries.close()
        val writtenReceipt = Files.write(receipt, "1\twrong-suite\t1\t3\t1\t1\t1\t1\t1\t1".getBytes(StandardCharsets.UTF_8))
        rejects(classOf[IllegalArgumentException]) { val _ = malformed.received(suite); () }
        val _ = Files.write(writtenReceipt, "1\tfixture.SelectedSuite\t1\t-1\t1\t1\t1\t1\t1\t1".getBytes(StandardCharsets.UTF_8))
        rejects(classOf[IllegalArgumentException]) { val _ = malformed.received(suite); () }
      } finally malformed.close()
      println("FORK_RECEIPT_CHECK_OK fresh ownership and cleanup")
      println("FORK_RECEIPT_CHECK_OK malformed filesystem identities and counts")
    } finally deleteEmpty(directory)
  }

  private def contract(label: String, create: () => ContractViews): Unit = {
    val views = create()
    val store = views.writer
    val reader = views.reader
    try {
      require(reader.received(suite).isEmpty, "New receipt store is not empty")
      val waiting = new CountDownLatch(2)
      val outcome = new AtomicReference[Option[Throwable]](None)
      val finished = new CountDownLatch(1)
      val observed = new ForkReceiptStore {
        override def publish(name: ForkReceiptSuite, value: ForkReceiptSummary): Unit = store.publish(name, value)
        override def received(name: ForkReceiptSuite): Option[ForkReceiptSummary] = {
          val result = reader.received(name)
          waiting.countDown()
          result
        }
        override def close(): Unit = store.close()
      }
      val awaiter = new ForkReceiptAwaiter(observed, ForkReceiptWaitPolicy(TimeUnit.SECONDS.toNanos(WaitSeconds), PollMillis))
      val target = new Thread(() => {
        try awaiter.await(Map(suite -> summary))
        catch { case cause: Throwable => outcome.set(Some(cause)) }
        finally finished.countDown()
      })
      target.start()
      try {
        require(waiting.await(WaitSeconds, TimeUnit.SECONDS), "Target did not wait for an absent receipt")
        require(finished.getCount == 1, "Target completed before host delivery")
        store.publish(suite, summary)
        require(finished.await(WaitSeconds, TimeUnit.SECONDS), "Target did not observe delivered counts")
        require(outcome.get().isEmpty, "Matching receipt failed: " + outcome.get())
      } finally { target.interrupt(); target.join(TimeUnit.SECONDS.toMillis(WaitSeconds)) }
      require(!target.isAlive, "Receipt waiter survived its fixture")
      new ForkReceiptAwaiter(reader, ForkReceiptWaitPolicy(1L, PollMillis)).await(Map.empty)
      new ForkReceiptAwaiter(reader, ForkReceiptWaitPolicy(1L, PollMillis)).await(Map(suite -> summary))
      val absent = ForkReceiptSuite("fixture.AbsentSuite")
      rejects(classOf[IllegalArgumentException]) { new ForkReceiptAwaiter(reader, ForkReceiptWaitPolicy(1L, PollMillis)).await(Map(absent -> summary)) }
      val changed = ForkReceiptSummary(1, counts.copy(success = 2, failure = 2))
      rejects(classOf[IllegalArgumentException]) { new ForkReceiptAwaiter(reader, ForkReceiptWaitPolicy(1L, PollMillis)).await(Map(suite -> changed)) }
      val repeated = ForkReceiptSummary(2, counts)
      rejects(classOf[IllegalArgumentException]) { new ForkReceiptAwaiter(reader, ForkReceiptWaitPolicy(1L, PollMillis)).await(Map(suite -> repeated)) }
      store.publish(suite, repeated)
      new ForkReceiptAwaiter(reader, ForkReceiptWaitPolicy(1L, PollMillis)).await(Map(suite -> repeated))
      rejects(classOf[IllegalArgumentException]) { new ForkReceiptAwaiter(reader, ForkReceiptWaitPolicy(1L, PollMillis)).await(Map(suite -> summary)) }
      store.publish(suite, summary)
      val unicode = ForkReceiptSuite("fixture.符号𝔘")
      store.publish(unicode, summary)
      require(reader.received(unicode).contains(summary), "Valid supplementary Unicode did not round-trip")
      Thread.currentThread().interrupt()
      try rejects(classOf[InterruptedException]) { awaiter.await(Map(absent -> summary)) }
      finally { val _ = Thread.interrupted() }
      println("FORK_RECEIPT_CHECK_OK " + label + " held delivery, exact counts, empty selection, timeout and interruption")
    } finally store.close()
    store.close()
    rejects(classOf[IllegalArgumentException]) { val _ = reader.received(suite); () }
  }

  private final case class ContractViews(writer: ForkReceiptStore, reader: ForkReceiptReader)

  private[protocol] def memoryStore(): ForkReceiptStore = new MemoryStore

  private def rejects[A <: Throwable](expected: Class[A])(body: => Unit): Unit = {
    var cause = Option.empty[Throwable]
    try body catch { case value: Throwable => cause = Some(value) }
    require(cause.exists(expected.isInstance), "Expected " + expected.getName + ", observed " + cause)
  }

  private def deleteEmpty(path: Path): Unit = {
    val entries = Files.list(path)
    try require(!entries.findAny().isPresent, "Fixture left owned receipt directories behind")
    finally entries.close()
    Files.delete(path)
  }

  private final class MemoryStore extends ForkReceiptStore {
    private var values = Map.empty[ForkReceiptSuite, ForkReceiptSummary]
    private var closed = false
    override def publish(name: ForkReceiptSuite, value: ForkReceiptSummary): Unit = synchronized { require(!closed); values = values.updated(name, value) }
    override def received(name: ForkReceiptSuite): Option[ForkReceiptSummary] = synchronized { require(!closed); values.get(name) }
    override def close(): Unit = synchronized { closed = true; values = Map.empty }
  }
}
