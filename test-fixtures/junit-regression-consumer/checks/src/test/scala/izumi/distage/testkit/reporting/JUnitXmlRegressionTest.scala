package izumi.distage.testkit.reporting

import izumi.distage.testkit.runner.spec.AnyWordSpec

import java.nio.file.{Files, Paths}
import javax.xml.parsers.DocumentBuilderFactory

final class JUnitXmlRegressionTest extends AnyWordSpec {
  private val testSleepMillis: Long = 2000L
  private val minExpectedPerTestSeconds: Double = testSleepMillis / 1000.0
  private val expectedFullTestNames = (1 to 4).map(index => s"intra-suite parallel sleeps should parallel sleep test $index")

  "intra-suite parallel tests must each be reported in JUnit XML with non-zero per-test time" in {
    val xmlFile = Paths.get(sys.props("izumi.junit.report"))
    assert(Files.isRegularFile(xmlFile), s"JUnit XML file was not produced at $xmlFile")
    val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile.toFile)
    val reportedTestCount = xml.getDocumentElement.getAttribute("tests").toInt
    assert(reportedTestCount >= expectedFullTestNames.size, s"JUnit XML claims $reportedTestCount tests")
    val nodes = xml.getElementsByTagName("testcase")
    val testcases = (0 until nodes.getLength).map { index =>
      val testcase = nodes.item(index).asInstanceOf[org.w3c.dom.Element]
      testcase.getAttribute("name") -> testcase.getAttribute("time").toDouble
    }.toMap
    expectedFullTestNames.foreach { name =>
      val time = testcases.getOrElse(name, fail(s"Expected testcase $name in JUnit XML, found: ${testcases.keys.mkString(", ")}"))
      assert(time >= minExpectedPerTestSeconds, s"Testcase $name duration $time is below $minExpectedPerTestSeconds seconds")
      println("JUNIT_PARALLEL_DURATION_OK name=" + name + " seconds=" + time)
    }
  }
}
