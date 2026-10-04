package izumi.distage.testkit.protocol

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest

final case class ForkReceiptSuite(value: String) {
  require(value.nonEmpty && !value.exists(character => character == '\n' || character == '\r' || character == '\t'), "Invalid fork receipt suite name")
  require(StandardCharsets.UTF_8.newEncoder().canEncode(value), "Fork receipt suite name contains malformed UTF-16")
}

final case class ForkReceiptCounts(success: Int, failure: Int, error: Int, skipped: Int, ignored: Int, canceled: Int, pending: Int) {
  require(Vector(success, failure, error, skipped, ignored, canceled, pending).forall(_ >= 0), "Negative fork receipt event count")

  def +(other: ForkReceiptCounts): ForkReceiptCounts = ForkReceiptCounts(
    Math.addExact(success, other.success), Math.addExact(failure, other.failure), Math.addExact(error, other.error),
    Math.addExact(skipped, other.skipped), Math.addExact(ignored, other.ignored), Math.addExact(canceled, other.canceled), Math.addExact(pending, other.pending),
  )
}

final case class ForkReceiptSummary(groups: Int, counts: ForkReceiptCounts) {
  require(groups > 0, "A fork receipt must complete at least one group")
}

trait ForkReceiptReader {
  def received(suite: ForkReceiptSuite): Option[ForkReceiptSummary]
}

trait ForkReceiptStore extends ForkReceiptReader {
  def publish(suite: ForkReceiptSuite, summary: ForkReceiptSummary): Unit
  def close(): Unit
}

final class FileForkReceiptStore private (val directory: Path) extends ForkReceiptStore {
  private final val SchemaVersion = "1"
  private var closed = false
  private var cleaned = false

  override def publish(suite: ForkReceiptSuite, summary: ForkReceiptSummary): Unit = synchronized {
    requireOpen()
    val counts = summary.counts
    val values = Vector(summary.groups, counts.success, counts.failure, counts.error, counts.skipped, counts.ignored, counts.canceled, counts.pending)
    val bytes = (Vector(SchemaVersion, suite.value) ++ values.map(_.toString)).mkString("\t").getBytes(StandardCharsets.UTF_8)
    val temporary = Files.createTempFile(directory, "pending-", ".receipt")
    try {
      val written = Files.write(temporary, bytes, StandardOpenOption.WRITE)
      val _ = Files.move(written, receiptPath(suite), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally { val _ = Files.deleteIfExists(temporary) }
  }

  override def received(suite: ForkReceiptSuite): Option[ForkReceiptSummary] = synchronized {
    requireOpen()
    val path = receiptPath(suite)
    if (!Files.exists(path)) None
    else {
      val fields = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).split("\t", -1).toVector
      require(fields.size == 10 && fields.head == SchemaVersion && fields(1) == suite.value, "Malformed fork receipt: " + path)
      val values = fields.drop(2).map(_.toInt)
      Some(ForkReceiptSummary(values.head, ForkReceiptCounts(values(1), values(2), values(3), values(4), values(5), values(6), values(7))))
    }
  }

  override def close(): Unit = synchronized {
    if (!cleaned) {
      closed = true
      val entries = Files.list(directory)
      try {
        val iterator = entries.iterator()
        while (iterator.hasNext) {
          val path = iterator.next()
          require(!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS), "Unexpected directory in owned fork receipts: " + path)
          Files.delete(path)
        }
      } finally entries.close()
      Files.delete(directory)
      cleaned = true
    }
  }

  private def receiptPath(suite: ForkReceiptSuite): Path = {
    val digest = MessageDigest.getInstance("SHA-256").digest(suite.value.getBytes(StandardCharsets.UTF_8))
    directory.resolve(digest.map(byte => f"${byte & 0xff}%02x").mkString + ".receipt")
  }

  private def requireOpen(): Unit = require(!closed && Files.isDirectory(directory), "Fork receipt directory is closed: " + directory)
}

object FileForkReceiptStore {
  def create(parent: Path): FileForkReceiptStore = {
    require(parent.isAbsolute, "Fork receipt parent must be absolute")
    val _ = Files.createDirectories(parent)
    new FileForkReceiptStore(Files.createTempDirectory(parent, "command-"))
  }

  def open(directory: Path): ForkReceiptReader = {
    require(directory.isAbsolute && Files.isDirectory(directory), "Fork receipt directory must be an existing absolute path")
    val store = new FileForkReceiptStore(directory)
    new ForkReceiptReader {
      override def received(suite: ForkReceiptSuite): Option[ForkReceiptSummary] = store.received(suite)
    }
  }
}

final case class ForkReceiptWaitPolicy(timeoutNanos: Long, pollMillis: Long) {
  require(timeoutNanos > 0 && pollMillis > 0, "Fork receipt waiting must be finite with a positive poll interval")
}

final class ForkReceiptAwaiter(store: ForkReceiptReader, policy: ForkReceiptWaitPolicy) {
  def await(expected: Map[ForkReceiptSuite, ForkReceiptSummary]): Unit = {
    val started = System.nanoTime()
    var pending = expected
    while (pending.nonEmpty) {
      pending = expected.filter { case (suite, summary) =>
        store.received(suite) match {
          case None => true
          case Some(actual) if actual.groups < summary.groups => true
          case Some(actual) =>
            require(actual == summary, "Fork receipt differs from emitted events: suite=" + suite.value + " expected=" + summary + " received=" + actual)
            false
        }
      }
      if (pending.nonEmpty) {
        require(System.nanoTime() - started < policy.timeoutNanos, "Incomplete fork receipt: " + pending)
        Thread.sleep(policy.pollMillis)
      }
    }
  }
}
