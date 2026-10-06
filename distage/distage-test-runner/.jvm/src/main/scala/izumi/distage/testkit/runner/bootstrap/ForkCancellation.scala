package izumi.distage.testkit.runner.bootstrap

import java.nio.file.{Files, Path}
import scala.util.control.NonFatal

private[bootstrap] final class ForkCancellation(directory: Path, cancel: () => Unit) extends AutoCloseable {
  private final val PollMillis = 5L
  @volatile private var closed = false
  private var failure = Option.empty[Throwable]
  private val thread = new Thread(() => {
    try {
      val signal = directory.resolve("cancel")
      while (!closed && !Files.isRegularFile(signal)) Thread.sleep(PollMillis)
      if (!closed) {
        require(Files.readString(signal) == "cancel", "Invalid fork cancellation request")
        cancel()
      }
    } catch { case NonFatal(cause) => synchronized { failure = Some(cause) }; cancel() }
  }, "distage-fork-cancellation")
  thread.start()

  override def close(): Unit = {
    closed = true
    thread.join()
    synchronized { failure.foreach(throw _) }
  }
}
