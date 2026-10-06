package izumi.distage.sbt

import java.nio.file.{Files, Paths}
import sbt.{JUnitXmlTestsListener, TestEvent, TestResult}
import sbt.testing.{Event, Fingerprint, OptionalThrowable, Selector, Status, SubclassFingerprint, TestSelector}
import scala.xml.Elem

object HostJUnitReportsTest {
  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "JUnit checks require a new fixture directory")
    val directory = Paths.get(arguments.head).toAbsolutePath
    val _ = Files.createDirectory(directory)
    Seq(HostJUnitFileFormat.Standard, HostJUnitFileFormat.Legacy).foreach { format =>
      run("memory", format, new MemoryFiles)
      run("file", format, new FileHostJUnitReports(Files.createDirectory(directory.resolve(format.toString))))
      listenerChecks(Files.createDirectory(directory.resolve(format.toString + "-listener")), format)
    }
    require(HostJUnitFileFormat.configured(Some("ALWAYS"), None) == HostJUnitFileFormat.Legacy)
    require(HostJUnitFileFormat.configured(None, Some("1")) == HostJUnitFileFormat.Legacy)
    require(HostJUnitFileFormat.configured(Some("auto"), Some("true")) == HostJUnitFileFormat.Standard)
    require(HostJUnitFileFormat.configured(None, None) == HostJUnitFileFormat.Standard)
    println("HOST_JUNIT_CHECK_OK configured property/environment precedence")
    val entries = Files.walk(directory)
    try entries.sorted(java.util.Comparator.reverseOrder()).forEach(path => { val _ = Files.delete(path) })
    finally entries.close()
  }

  private def run(mode: String, format: HostJUnitFileFormat, files: HostJUnitReportFiles): Unit = {
    val left = HostSuiteName("fixture.Left")
    val right = HostSuiteName("fixture.Right")
    val reports = new HostJUnitReports(files, format)
    val first = report(left, "first", <testcase classname="fixture.Nested" name="same" time="1.25"/> )
    files.replace(format.filename(left), first)
    reports.completedGroup(left)
    require(files.load(format.filename(left)) == first, "Single group XML changed")
    println("HOST_JUNIT_CHECK_OK " + mode + " " + format + " unchanged single group")

    val second = report(left, "second", <testcase classname="fixture.Nested" name="same" time="1.25"><error message="original cause" type="fixture.Failure">original trace</error></testcase>)
    val other = report(right, "other", <testcase classname="fixture.Other" name="other" time="1.25"><failure message="other failure"/></testcase>)
    files.replace(format.filename(right), other)
    reports.completedGroup(right)
    files.replace(format.filename(left), second)
    reports.completedGroup(left)
    val third = report(left, "third", <testcase classname="fixture.Nested" name="same" time="1.25"><skipped/></testcase>)
    files.replace(format.filename(left), third)
    reports.completedGroup(left)
    val merged = files.load(format.filename(left))
    require(Seq("tests", "errors", "failures", "skipped", "time").map(merged \@ _) == Seq("3", "1", "0", "1", "3.75"), "Merged counts/duration differ")
    require((merged \ "testcase") == (first \ "testcase") ++ (second \ "testcase") ++ (third \ "testcase"), "Original testcase selectors or failure payloads changed")
    require((merged \ "properties") == (first \ "properties"), "Report properties changed")
    require((merged \ "system-out").text == "firstsecondthird" && (merged \ "system-err").text == "firstsecondthird", "Captured test output was lost")
    require(files.load(format.filename(right)) == other, "Another suite was merged into this suite")
    println("HOST_JUNIT_CHECK_OK " + mode + " " + format + " duplicate selectors, statuses, cause, output and suite isolation")

    val fresh = new HostJUnitReports(files, format)
    files.replace(format.filename(left), first)
    fresh.completedGroup(left)
    require(files.load(format.filename(left)) == first, "A new command retained prior XML groups")
    println("HOST_JUNIT_CHECK_OK " + mode + " " + format + " fresh command")

    val cancelledCase = <testcase classname="fixture.Nested" name="cancelled" time="1.25"/>
    val cancellation = report(left, "cancelled-output", cancelledCase)
    files.replace(format.filename(left), cancellation)
    new HostJUnitReports(files, format).completedGroup(left, Vector(cancelledCase))
    val projected = files.load(format.filename(left))
    require((projected \@ "skipped") == "1" && (projected \ "testcase" \ "skipped" \@ "message") == "Cancelled", "Cancelled case looks passed or loses its status")
    require((projected \ "system-out") == (cancellation \ "system-out"), "Cancellation projection changed captured output")
    require((projected \ "testcase" \@ "name") == "cancelled", "Cancellation projection changed its test identity")
    println("HOST_JUNIT_CHECK_OK " + mode + " " + format + " cancelled outcome and output")

    val ambiguity = cancellation.copy(child = cancellation.child ++ Vector(cancelledCase), attributes = new scala.xml.UnprefixedAttribute("tests", "2", cancellation.attributes.remove("tests")))
    files.replace(format.filename(left), ambiguity)
    rejects(new HostJUnitReports(files, format).completedGroup(left, Vector(cancelledCase)))
    require(files.load(format.filename(left)) == ambiguity, "Ambiguous cancellation modified the report")
    println("HOST_JUNIT_CHECK_OK " + mode + " " + format + " ambiguous cancellation rejection")

    files.replace(format.filename(left), other)
    rejects(fresh.completedGroup(left))
    println("HOST_JUNIT_CHECK_OK " + mode + " " + format + " mismatched report rejection")

    val sentinel = new IllegalStateException("report publication failed")
    val failing = new HostJUnitReportFiles {
      override def load(name: HostJUnitFileName): Elem = files.load(name)
      override def replace(name: HostJUnitFileName, report: Elem): Unit = throw sentinel
    }
    val failed = new HostJUnitReports(failing, format)
    files.replace(format.filename(left), first)
    failed.completedGroup(left)
    files.replace(format.filename(left), second)
    try { failed.completedGroup(left); throw new AssertionError("Report publication unexpectedly succeeded") }
    catch { case cause: IllegalStateException => require(cause eq sentinel, "Original publication failure changed") }
    println("HOST_JUNIT_CHECK_OK " + mode + " " + format + " publication Throwable identity")
  }

  private def listenerChecks(directory: java.nio.file.Path, format: HostJUnitFileFormat): Unit = {
    val owned = HostSuiteName("fixture.CancelledSuite")
    val foreign = HostSuiteName("fixture.ForeignSuite")
    val receipt = new HostReceipt(new izumi.distage.sbt.target.TaskGroups.MemoryStore)
    val _ = receipt.configure(Set(owned))
    val original = new JUnitXmlTestsListener(directory.toFile, format == HostJUnitFileFormat.Legacy, sbt.util.Logger.Null)
    val listener = new HostJUnitReportListener(original, new HostJUnitReports(new FileHostJUnitReports(directory), format), receipt)
    listener.doInit()
    Seq(owned, foreign).foreach { suite =>
      listener.startGroup(suite.value)
      listener.testEvent(TestEvent(Seq(event(suite, "cancelled", Status.Canceled), event(suite, "skipped", Status.Skipped), event(suite, "passed", Status.Success))))
      listener.endGroup(suite.value, TestResult.Error)
      val report = scala.xml.XML.loadFile(directory.resolve(format.filename(suite).value).toFile)
      val byName = (report \ "testcase").map(value => (value \@ "name") -> value).toMap
      require(byName.size == 3 && (byName("skipped") \ "skipped").size == 1 && (byName("passed") \ "skipped").isEmpty)
      if (suite == owned) require((report \@ "skipped") == "2" && (byName("cancelled") \ "skipped" \@ "message") == "Cancelled")
      else require((report \@ "skipped") == "1" && (byName("cancelled") \ "skipped").isEmpty, "Foreign JUnit semantics changed")
    }
    println("HOST_JUNIT_CHECK_OK " + format + " public SDK owned cancellation and unchanged foreign outcomes")
  }

  private def event(suite: HostSuiteName, name: String, value: Status): Event = new Event {
    override def fullyQualifiedName(): String = suite.value
    override def fingerprint(): Fingerprint = new SubclassFingerprint {
      override def isModule(): Boolean = false
      override def superclassName(): String = "fixture.Suite"
      override def requireNoArgConstructor(): Boolean = true
    }
    override def selector(): Selector = new TestSelector(name)
    override def status(): Status = value
    override def throwable(): OptionalThrowable = new OptionalThrowable
    override def duration(): Long = 1250L
  }

  private def report(name: HostSuiteName, output: String, testcase: Elem): Elem = {
    <testsuite name={name.value} tests="1" errors={(testcase \ "error").size.toString} failures={(testcase \ "failure").size.toString} skipped={(testcase \ "skipped").size.toString} time="1.25" timestamp="2026-10-05T00:00:00" hostname="fixture">
      <properties><property name="fixture" value="original"/></properties>{testcase}
      <system-out>{output}</system-out><system-err>{output}</system-err>
    </testsuite>
  }

  private final class MemoryFiles extends HostJUnitReportFiles {
    private var reports = Map.empty[HostJUnitFileName, Elem]
    override def load(name: HostJUnitFileName): Elem = reports(name)
    override def replace(name: HostJUnitFileName, report: Elem): Unit = { reports = reports.updated(name, report) }
  }

  private def rejects(operation: => Unit): Unit = {
    try { operation; throw new AssertionError("Invalid report accepted") }
    catch { case _: IllegalArgumentException => () }
  }
}
