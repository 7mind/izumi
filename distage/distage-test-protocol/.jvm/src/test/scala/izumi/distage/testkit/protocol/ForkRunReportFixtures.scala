package izumi.distage.testkit.protocol

import java.nio.file.{Files, Paths}
import java.util.UUID

object ForkRunReportFixtures {
  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "Fork report checks require a new directory")
    val directory = Files.createDirectory(Paths.get(arguments.head).toAbsolutePath)
    try {
      contract("memory", new MemoryReports)
      contract("file", new FileForkRunReports(directory))
      val entries = Files.list(directory)
      val path = try entries.findFirst().get() finally entries.close()
      val original = Files.readString(path)
      val _ = Files.writeString(path, "0\n" + original.linesIterator.drop(1).mkString("\n") + "\n")
      rejects { val _ = new FileForkRunReports(directory).completed() }
      val _ = Files.writeString(path, original)
      val changed = directory.resolve("different.run-report")
      val _ = Files.move(path, changed)
      rejects { val _ = new FileForkRunReports(directory).completed() }
      println("FORK_RUN_REPORT_CHECK_OK corrupt schema and run identity rejected")
    } finally {
      val entries = Files.list(directory)
      try entries.forEach(path => { val _ = Files.delete(path) }) finally entries.close()
      Files.delete(directory)
    }
  }

  private def contract(mode: String, store: ForkRunReports): Unit = {
    val suite = SuiteId("logical-suite-λ\tline\n")
    val test = TestResult(TestId(BuildTargetId("target"), suite, Vector("one two", "three"), Some("variant")), TestStatus.Cancelled, None, 10L)
    val report = ForkRunReport(RunOutcome(RunId(UUID.randomUUID().toString), Vector(test), Vector.empty, cancelled = true), Vector(ForkSuiteOwner("fixture.λSuite\tline\n", Some(suite))))
    require(store.completed().isEmpty, "New report store retained an old run")
    store.publish(report)
    require(store.completed() == Vector(report), "Fork report lost logical identity or cancellation")
    rejects(store.publish(report))
    require(store.completed() == Vector(report), "Duplicate publication overwrote a completed report")
    println("FORK_RUN_REPORT_CHECK_OK " + mode + " terminal cancellation, Unicode identities and duplicate rejection")
    rejects { val _ = ForkRunReport(report.outcome, Vector.empty) }
    rejects { val _ = ForkRunReport(report.outcome, report.owners ++ report.owners) }
    rejects { val _ = ForkRunReport(report.outcome, Vector(ForkSuiteOwner("different", Some(SuiteId("different"))))) }
    println("FORK_RUN_REPORT_CHECK_OK " + mode + " missing and duplicate owners rejected")
    val failure = Failure(FailurePhase.Discovery, "TestApplicationError", "Duplicate suite identities", Vector.empty, Vector.empty, None, Vector.empty, Vector.empty)
    val failed = ForkRunReport(RunOutcome(RunId(UUID.randomUUID().toString), Vector.empty, Vector(failure), cancelled = false), Vector(ForkSuiteOwner("left", Some(suite)), ForkSuiteOwner("right", Some(suite))))
    store.publish(failed)
    require(store.completed().toSet == Set(report, failed), "Invalid discovery aliases erased the original failure")
    rejects { val _ = failed.copy(outcome = failed.outcome.copy(results = Vector(test))) }
    println("FORK_RUN_REPORT_CHECK_OK " + mode + " duplicate logical IDs retain discovery failure and reject ambiguous results")
  }

  private final class MemoryReports extends ForkRunReports {
    private var values = Vector.empty[ForkRunReport]
    override def publish(report: ForkRunReport): Unit = {
      require(!values.exists(_.outcome.run == report.outcome.run), "Duplicate run report")
      values :+= report
    }
    override def completed(): Vector[ForkRunReport] = values
  }

  private def rejects(operation: => Unit): Unit = {
    try { operation; throw new AssertionError("Invalid fork report accepted") }
    catch { case _: IllegalArgumentException => () }
  }
}
