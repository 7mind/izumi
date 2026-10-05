package izumi.distage.sbt

import sbt.{JUnitXmlTestsListener, TestDefinition, TestEvent, TestReportListener, TestResult, TestsListener, taskKey}

import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.Locale
import scala.util.control.NonFatal
import scala.xml.{Elem, PCData, UnprefixedAttribute, XML}

private[sbt] enum HostJUnitFileFormat(val prefix: String) {
  case Standard extends HostJUnitFileFormat("TEST-")
  case Legacy extends HostJUnitFileFormat("")

  def filename(name: HostSuiteName): HostJUnitFileName = HostJUnitFileName(prefix + name.value.replaceAll("\\s+", "-") + ".xml")
}

private[sbt] object HostJUnitFileFormat {
  val Property = "sbt.testing.legacyreport"
  val Environment = "SBT_TESTING_LEGACYREPORT"

  def configured(property: Option[String], environment: Option[String]): HostJUnitFileFormat = {
    if (property.orElse(environment).exists(value => Set("1", "always", "true").contains(value.toLowerCase(Locale.ENGLISH)))) Legacy else Standard
  }
}

private[sbt] final case class HostJUnitFileName(value: String) {
  require(value.nonEmpty && Path.of(value).getFileName.toString == value && !Path.of(value).isAbsolute, "JUnit report filename must be local to its directory")
}

private[sbt] trait HostJUnitReportFiles {
  def load(name: HostJUnitFileName): Elem
  def replace(name: HostJUnitFileName, report: Elem): Unit
}

private[sbt] final class FileHostJUnitReports(directory: Path) extends HostJUnitReportFiles {
  override def load(name: HostJUnitFileName): Elem = XML.loadFile(directory.resolve(name.value).toFile)
  override def replace(name: HostJUnitFileName, report: Elem): Unit = {
    val temporary = Files.createTempFile(directory, "distage-junit-", ".xml")
    try {
      XML.save(temporary.toString, report, "UTF-8", xmlDecl = true, null)
      val _ = Files.move(temporary, directory.resolve(name.value), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally { val _ = Files.deleteIfExists(temporary) }
  }
}

private[sbt] final class HostJUnitReports(files: HostJUnitReportFiles, format: HostJUnitFileFormat) {
  private var completed = Map.empty[HostSuiteName, Elem]

  def completedGroup(name: HostSuiteName): Unit = synchronized {
    val filename = format.filename(name)
    val report = files.load(filename)
    require(report.label == "testsuite" && (report \@ "name") == name.value, "JUnit report differs from its completed group")
    require((report \@ "tests").toInt == (report \ "testcase").size, "JUnit report test count differs from its cases")
    completed.get(name) match {
      case None => completed = completed.updated(name, report)
      case Some(previous) =>
        val merged = HostJUnitReports.merge(previous, report)
        files.replace(filename, merged)
        completed = completed.updated(name, merged)
    }
  }
}

private[sbt] object HostJUnitReports {
  val format = taskKey[HostJUnitFileFormat]("Configured SBT JUnit report filename format")

  def merge(previous: Elem, current: Elem): Elem = {
    var attributes = previous.attributes
    Seq("tests", "errors", "failures", "skipped").foreach { name =>
      val count = Math.addExact((previous \@ name).toInt, (current \@ name).toInt)
      require(count >= 0, "Negative merged JUnit count")
      attributes = new UnprefixedAttribute(name, count.toString, attributes.remove(name))
    }
    val duration = BigDecimal(previous \@ "time") + BigDecimal(current \@ "time")
    attributes = new UnprefixedAttribute("time", duration.toString, attributes.remove("time"))
    val output = Seq("system-out", "system-err").flatMap { label =>
      (previous \ label).collect { case element: Elem => element.copy(child = Seq(PCData(element.text + (current \ label).text))) }
    }
    val metadata = previous.child.filterNot(node => Set("testcase", "system-out", "system-err").contains(node.label))
    previous.copy(attributes = attributes, child = metadata ++ (previous \ "testcase") ++ (current \ "testcase") ++ output)
  }

  def wrap(listener: TestReportListener, receipt: HostReceipt, format: HostJUnitFileFormat): TestReportListener = listener match {
    case current: HostJUnitReportListener => wrap(current.inherited, receipt, format)
    case junit: JUnitXmlTestsListener => new HostJUnitReportListener(junit, new HostJUnitReports(new FileHostJUnitReports(junit.targetDir.toPath.toAbsolutePath), format), receipt)
    case other => other
  }
}

private[sbt] final class HostJUnitReportListener(val inherited: JUnitXmlTestsListener, reports: HostJUnitReports, receipt: HostReceipt) extends TestsListener {
  override def doInit(): Unit = inherited.doInit()
  override def startGroup(name: String): Unit = inherited.startGroup(name)
  override def testEvent(event: TestEvent): Unit = inherited.testEvent(event)
  override def contentLogger(test: TestDefinition): Option[sbt.ContentLogger] = inherited.contentLogger(test)
  override def doComplete(result: TestResult): Unit = inherited.doComplete(result)
  override def endGroup(name: String, cause: Throwable): Unit = finish(name)(inherited.endGroup(name, cause))
  override def endGroup(name: String, result: TestResult): Unit = finish(name)(inherited.endGroup(name, result))

  private def finish(name: String)(operation: => Unit): Unit = synchronized {
    try { operation; reports.completedGroup(HostSuiteName(name)) }
    catch { case NonFatal(cause) => receipt.failPublication(cause); throw cause }
  }
}
