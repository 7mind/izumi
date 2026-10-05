package izumi.distage.sbt

import java.nio.file.{Files, Paths}
import scala.xml.Elem

object HostJUnitReportsTest {
  def main(arguments: Array[String]): Unit = {
    require(arguments.length == 1, "JUnit checks require a new fixture directory")
    val directory = Paths.get(arguments.head).toAbsolutePath
    val _ = Files.createDirectory(directory)
    Seq(HostJUnitFileFormat.Standard, HostJUnitFileFormat.Legacy).foreach { format =>
      run("memory", format, new MemoryFiles)
      run("file", format, new FileHostJUnitReports(Files.createDirectory(directory.resolve(format.toString))))
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
