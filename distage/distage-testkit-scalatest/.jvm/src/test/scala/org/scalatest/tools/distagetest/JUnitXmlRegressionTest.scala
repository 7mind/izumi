// This test lives in `org.scalatest.tools.distagetest` so it can instantiate
// `org.scalatest.tools.JUnitXmlReporter`, which is `private[scalatest]`. The test
// otherwise has no dependency on the package and adds nothing to it.
package org.scalatest.tools.distagetest

import izumi.distage.testkit.scalatest.SpecIdentity
import izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistry
import izumi.fundamentals.platform.files.IzFiles
import org.scalatest.events.{Ordinal, SuiteCompleted, SuiteStarting}
import org.scalatest.tools.JUnitXmlReporter
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{Args, Tracker}

import java.nio.file.{Files, Path}
import scala.xml.XML

/** Regression coverage for two distinct silent-failure modes of
  * `distage-testkit-scalatest`'s integration with ScalaTest's XML reporters:
  *
  *   1. If the per-suite event linearizer
  *      (`ScalatestLinearizedTestReporter`) is bypassed for any reporter
  *      path while distage's intra-suite parallel test execution
  *      (`parallelTests = Parallelism.Unlimited`, the default) is in
  *      effect, the parallel `TestStarting → terminator` events for
  *      different tests of the same suite interleave. ScalaTest's
  *      `JUnitXmlReporter.processTest` then encounters a stray test
  *      event and throws `RuntimeException("unexpected ...")`. Under
  *      normal SBT/Intellij wiring that exception is swallowed by
  *      `CatchReporter` and the suite's `TEST-*.xml` file is silently
  *      never written.
  *
  *   2. If distage-testkit's reporter stops populating the explicit
  *      `timeStamp` field of each ScalaTest `Event` (relying on the
  *      `(new Date).getTime` case-class default), then for any wrapping
  *      reporter that batches/serialises events, every event in a
  *      flushed batch is allocated at the same wall-clock moment.
  *      `JUnitXmlReporter` derives `<testcase time="...">` from
  *      `terminator.timeStamp - testStarting.timeStamp`, which then
  *      collapses to ~0ms regardless of how long the test actually
  *      took.
  *
  * The test runs a small in-process distage-testkit suite with four
  * parallel sleeping tests through a real `JUnitXmlReporter`, parses
  * the resulting XML and asserts the minimum properties any correct
  * implementation must satisfy:
  *
  *   - the per-suite `TEST-<suiteId>.xml` file exists and parses;
  *   - it contains the four named tests we registered;
  *   - each test's reported `time` is roughly the real sleep wall
  *     time (with generous slack for CI jitter), not zero.
  *
  * Assertions are deliberately lower-bound only; extra tests or
  * larger times in the XML do not constitute a failure.
  *
  * The anonymous inner suite uses a private
  * [[DistageTestsRegistry]] (via the
  * [[org.scalatest.distage.DistageScalatestTestSuiteRunner#_distageTestsRegistry]]
  * injection point) so it is fully isolated from the process-wide
  * [[izumi.distage.testkit.services.scalatest.dstest.DistageTestsRegistrySingleton]]. SBT/ScalaTest only auto-discovers
  * top-level public suites, so the anonymous inner suite is invisible
  * to test discovery.
  */
final class JUnitXmlRegressionTest extends AnyWordSpec {

  private val testSleepMillis: Long = 2000L
  private val minExpectedPerTestSeconds: Double = testSleepMillis / 1000.0

  /** WordSpec scope under which the parallel tests are nested. The full test name
    * reported into JUnit XML is `"$parallelScope should $name"`.
    */
  private val parallelScope: String = "intra-suite parallel sleeps"

  private val parallelLeafNames: Seq[String] = Seq(
    "parallel sleep test 1",
    "parallel sleep test 2",
    "parallel sleep test 3",
    "parallel sleep test 4",
  )

  private val expectedFullTestNames: Seq[String] = parallelLeafNames.map(n => s"$parallelScope should $n")

  "intra-suite parallel tests must each be reported in JUnit XML with non-zero per-test time" in {
    val tempDir: Path = Files.createTempDirectory("distage-junit-regression")
    try {
      val xmlReporter = new JUnitXmlReporter(tempDir.toAbsolutePath.toString)

      val privateRegistry = new DistageTestsRegistry
      val leafNames = parallelLeafNames
      val scopeName = parallelScope
      val perTestSleep = testSleepMillis

      val suiteUnderTest: SpecIdentity = new SpecIdentity { spec =>
        override protected def _distageTestsRegistry: DistageTestsRegistry = privateRegistry

        scopeName.should {
          leafNames.foreach {
            name =>
              spec.convertToWordSpecStringWrapperDS(name) in {
                Thread.sleep(perTestSleep)
                ()
              }
          }
        }(using spec.subjectRegistrationFunction1)
      }

      val tracker = new Tracker(new Ordinal(0))
      val suiteName = suiteUnderTest.suiteName
      val suiteId = suiteUnderTest.suiteId
      val suiteClassName = suiteUnderTest.getClass.getName

      // ScalaTest's Framework emits SuiteStarting/SuiteCompleted around `suite.run`.
      // Because this test invokes `run` directly, those bookend events must be emitted by hand —
      // JUnitXmlReporter writes the per-suite XML file when it sees SuiteCompleted, and would
      // fail to locate the matching SuiteStarting if we omitted it.
      xmlReporter(
        SuiteStarting(
          ordinal = tracker.nextOrdinal(),
          suiteName = suiteName,
          suiteId = suiteId,
          suiteClassName = Some(suiteClassName),
        )
      )

      val status = suiteUnderTest.run(None, Args(reporter = xmlReporter, tracker = tracker))
      val succeeded = status.succeeds()
      assert(succeeded, "the in-process anonymous test suite must succeed before the XML is inspected")

      xmlReporter(
        SuiteCompleted(
          ordinal = tracker.nextOrdinal(),
          suiteName = suiteName,
          suiteId = suiteId,
          suiteClassName = Some(suiteClassName),
        )
      )

      val xmlFile = tempDir.resolve(s"TEST-$suiteId.xml").toFile
      assert(
        xmlFile.exists(),
        s"JUnit XML file was not produced at ${xmlFile.getAbsolutePath} — the per-suite TEST-*.xml file " +
        "is silently absent when the linearizer is bypassed and intra-suite parallel events interleave",
      )

      val xml = XML.loadFile(xmlFile)
      val reportedTestCount = (xml \ "@tests").text.toInt
      assert(
        reportedTestCount >= expectedFullTestNames.size,
        s"JUnit XML claims $reportedTestCount tests, expected at least ${expectedFullTestNames.size} (test names: ${expectedFullTestNames.mkString(", ")})",
      )

      val testcases = (xml \ "testcase").map(tc => (tc \ "@name").text -> (tc \ "@time").text.toDouble).toMap
      expectedFullTestNames.foreach {
        name =>
          val time = testcases.getOrElse(
            name,
            fail(s"""expected <testcase name="$name"> in JUnit XML, found: ${testcases.keys.mkString(", ")}"""),
          )
          assert(
            time >= minExpectedPerTestSeconds,
            s"""<testcase name="$name" time="$time"/> is below the minimum $minExpectedPerTestSeconds s — testkit must populate Event timestamps """ +
            "explicitly from its own Timing measurements; relying on the case-class `(new Date).getTime` default collapses " +
            "per-test times to ~0 under the per-suite event linearizer.",
          )
      }
    } finally {
      if (Files.exists(tempDir)) IzFiles.erase(tempDir)
    }
  }

}
