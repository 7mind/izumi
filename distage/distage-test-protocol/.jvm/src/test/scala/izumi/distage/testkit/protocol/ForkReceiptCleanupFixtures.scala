package izumi.distage.testkit.protocol

import java.nio.file.{Files, Paths}

object ForkReceiptCleanupFixtures {
  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Expected a fresh owned cleanup fixture directory")
    val parent = Paths.get(arguments.head).toAbsolutePath
    require(!Files.exists(parent), "Cleanup fixture directory must be new")
    val store = FileForkReceiptStore.create(parent)
    val blocker = Files.createDirectory(store.directory.resolve("fixture-blocker"))
    val suite = ForkReceiptSuite("fixture.CleanupSuite")
    val summary = ForkReceiptSummary(1, ForkReceiptCounts(1, 0, 0, 0, 0, 0, 0))
    try {
      store.publish(suite, summary)
      val reader = FileForkReceiptStore.open(store.directory)
      require(reader.received(suite).contains(summary), "Initial receipt publication differs")
      reject("Unexpected directory in owned fork receipts") { store.close() }
      require(Files.isDirectory(store.directory) && Files.isDirectory(blocker), "Failed cleanup removed its obstruction")
      reject("Fork receipt directory is closed") { store.publish(suite, summary) }
      reject("Fork receipt directory is closed") { val _ = store.received(suite) }
      println("FORK_RECEIPT_CLEANUP_FAULT_OK surfaced=true publication=closed")
      Files.delete(blocker)
      store.close()
      require(!Files.exists(store.directory), "CLEANUP_RETRY_LEFT_OWNED_DIRECTORY")
      store.close()
      println("FORK_RECEIPT_CLEANUP_RETRY_OK removed=true repeated=closed")
    } finally {
      val _ = Files.deleteIfExists(blocker)
      if (Files.isDirectory(store.directory)) {
        val entries = Files.list(store.directory)
        try {
          val iterator = entries.iterator()
          while (iterator.hasNext) {
            val path = iterator.next()
            require(Files.isRegularFile(path), "Unexpected cleanup-fixture residue: " + path)
            Files.delete(path)
          }
        } finally entries.close()
        Files.delete(store.directory)
      }
      Files.delete(parent)
    }
  }

  private def reject(message: String)(operation: => Unit): Unit = {
    var failure = Option.empty[IllegalArgumentException]
    try operation catch { case cause: IllegalArgumentException => failure = Some(cause) }
    require(failure.exists(_.getMessage.contains(message)), "Cleanup fixture did not reproduce " + message + ": " + failure)
  }
}
