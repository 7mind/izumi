package izumi.distage.testkit.runner.bootstrap

import izumi.distage.testkit.protocol.{FileForkRunReports, ForkCompletionOwnership, SuiteId}
import izumi.distage.testkit.runner.spec.AnyWordSpec
import sbt.testing.{Event, EventHandler, Logger, Status, SuiteSelector, TaskDef}

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.CollectionConverters._

object ForkReportFailureFixtures {
  private final val WaitSeconds = 10L

  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Fork report failure checks require a new directory")
    val parent = Files.createDirectory(Paths.get(arguments.head).toAbsolutePath)
    val property = ForkCompletionOwnership.DIRECTORY_PROPERTY
    val previous = Option(System.getProperty(property))
    try {
      Seq("duplicate", "publication").foreach(mode => run(Files.createDirectory(parent.resolve(mode)), mode))
    } finally {
      val _ = previous.fold(System.clearProperty(property))(value => System.setProperty(property, value))
      val entries = Files.walk(parent)
      try entries.sorted(java.util.Comparator.reverseOrder()).forEach(path => { val _ = Files.delete(path) }) finally entries.close()
    }
  }

  private def run(directory: Path, mode: String): Unit = {
    val _ = System.setProperty(ForkCompletionOwnership.DIRECTORY_PROPERTY, directory.toString)
    val framework = new Framework
    val arguments = Array("--build-id", "report-build", "--target-id", "report-target", "--catalogue-id", "report-catalogue", "--distage-host-receipts", directory.toString, "--distage-command-completion")
    val runner = framework.runner(arguments, Array("--distage-fork-receipts", directory.toString), getClass.getClassLoader)
    val names = if (mode == "duplicate") Array(classOf[ForkReportDuplicateLeft].getName, classOf[ForkReportDuplicateRight].getName)
    else Array(classOf[ForkReportValidLeft].getName, classOf[ForkReportValidRight].getName)
    val tasks = runner.tasks(names.map(name => new TaskDef(name, framework.fingerprints().head, false, Array(new SuiteSelector))))
    val completed = new CountDownLatch(tasks.length)
    val failures = new ConcurrentLinkedQueue[Throwable]
    val events = new ConcurrentLinkedQueue[Event]
    val removed = new AtomicBoolean(false)
    val handler = new EventHandler {
      override def handle(event: Event): Unit = {
        val _ = events.add(event)
        if (mode == "publication" && event.status() == Status.Success && removed.compareAndSet(false, true)) Files.delete(directory)
      }
    }
    val threads = tasks.map { task =>
      val thread = new Thread(() => {
        try { val _ = task.execute(handler, Array.empty[Logger]) }
        catch { case cause: Throwable => val _ = failures.add(cause) }
        finally completed.countDown()
      }, "fork-report-failure-fixture")
      thread.setDaemon(true)
      thread.start()
      thread
    }
    require(completed.await(WaitSeconds, TimeUnit.SECONDS), "Fork report failure left a sibling task pending")
    threads.foreach(_.join())
    require(failures.isEmpty && runner.done() == "", "Fork failure escaped its terminal projection")
    val observed = events.iterator().asScala.toVector
    val errors = observed.filter(_.status() == Status.Error)
    require(errors.size == tasks.length && errors.map(_.fullyQualifiedName()).toSet == names.toSet, "Fork failure lost a selected suite")
    if (mode == "duplicate") {
      require(observed.size == tasks.length && errors.forall(_.throwable().get().getMessage.contains("Discovery: TestApplicationError: Duplicate suite identities")))
      val reports = new FileForkRunReports(directory).completed()
      require(reports.size == 1 && reports.head.outcome.results.isEmpty && reports.head.outcome.failures.size == 1)
    } else {
      require(observed.count(_.status() == Status.Success) == tasks.length, "Publication failure relaunched test bodies")
      require(errors.forall(_.throwable().get().getMessage.contains("Transport: java.lang.IllegalArgumentException: requirement failed: Fork report directory")))
    }
    println("FORK_REPORT_FAILURE_CHECK_OK " + mode + " tasks=joined failure=shared original=retained")
  }
}

abstract class ForkReportDuplicateSuite extends AnyWordSpec {
  override protected def suiteId: SuiteId = SuiteId("shared-logical-id")
  "scope" should { "test" in { () } }
}
final class ForkReportDuplicateLeft extends ForkReportDuplicateSuite
final class ForkReportDuplicateRight extends ForkReportDuplicateSuite
final class ForkReportValidLeft extends AnyWordSpec { "scope" should { "test" in { () } } }
final class ForkReportValidRight extends AnyWordSpec { "scope" should { "test" in { () } } }
