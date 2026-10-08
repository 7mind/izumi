import javax.xml.parsers.DocumentBuilderFactory
import sbt.complete.DefaultParsers.spaceDelimited

ThisBuild / scalaVersion := sys.props("izumi.fixture.scala-version")
ThisBuild / organization := "izumi.local.fixtures"
ThisBuild / version := "0.0.0"
ThisBuild / publish / skip := true

val prepareFixture = inputKey[Unit]("Remove only this fixture's execution records and reports")
val verifyFixture = inputKey[Unit]("Compare actual body execution records with host JUnit reports")

libraryDependencies += "io.7mind.izumi" %% "distage-test-runner" % sys.props("izumi.fixture.version") % Test
scalacOptions ++= {
  if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1") else Seq("-release:17", "-Xsource:3")
}
Test / testFrameworks := Seq(new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"))
Test / testOptions += Tests.Argument(
  new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"),
  "--build-id", "published-framework-consumer",
  "--target-id", "framework-consumer-jvm",
  "--catalogue-id", "five-suite-catalogue",
)
Test / javaOptions += "-Dizumi.fixture.audit-root=" + file(sys.props("izumi.fixture.audit-root")).getAbsolutePath

prepareFixture := {
  val labels = spaceDelimited("suite labels").parsed
  require(labels.nonEmpty, "Expected suite labels must be explicit")
  val audit = file(sys.props("izumi.fixture.audit-root"))
  IO.delete(audit)
  IO.createDirectory(audit)
  IO.delete(target.value / "test-reports")
  streams.value.log.info("FRAMEWORK_CASE_PREPARED suites=" + labels.mkString(","))
}

verifyFixture := {
  val labels = spaceDelimited("suite labels").parsed
  require(labels.nonEmpty && labels.distinct.size == labels.size, "Expected suite labels must be explicit and unique")
  val prefix = "izumi.fixtures.bootstrap."
  val expectedBodies = labels.flatMap(label => (1 to 3).map(index => prefix + label + "\t" + index)).sorted
  val records = (file(sys.props("izumi.fixture.audit-root")) * "*.body").get().sorted
  val bodies = records.map(file => IO.read(file).trim).sorted
  require(bodies == expectedBodies, "Actual body records differ: " + bodies + " expected " + expectedBodies)
  val reports = (target.value / "test-reports" * "*.xml").get()
  val factory = DocumentBuilderFactory.newInstance()
  val cases = reports.flatMap { report =>
    val document = factory.newDocumentBuilder().parse(report)
    require(document.getElementsByTagName("failure").getLength == 0 && document.getElementsByTagName("error").getLength == 0, "Host report contains a failure: " + report)
    val nodes = document.getElementsByTagName("testcase")
    (0 until nodes.getLength).map { index =>
      val node = nodes.item(index).asInstanceOf[org.w3c.dom.Element]
      node.getAttribute("classname") -> node.getAttribute("name")
    }
  }
  val expectedCases = labels.flatMap(label => Seq("equal display name should sync", "equal display name should future", "equal display name should nested should final").map(name => (prefix + label) -> name)).sorted
  require(cases.sorted == expectedCases, "Host test identities differ: " + cases.sorted + " expected " + expectedCases)
  streams.value.log.info("PUBLISHED_FRAMEWORK_HOST_OK suites=" + labels.size + " bodies=" + bodies.size + " reported=" + cases.size)
}
