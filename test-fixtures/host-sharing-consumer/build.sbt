import javax.xml.parsers.DocumentBuilderFactory
import sbt.complete.DefaultParsers.spaceDelimited

ThisBuild / scalaVersion := sys.props("izumi.fixture.scala-version")
ThisBuild / organization := "izumi.local.fixtures"
ThisBuild / version := "0.0.0"
ThisBuild / publish / skip := true

val prepareFixture = inputKey[Unit]("Prepare the owned host fixture audit directory")
val verifyFixture = inputKey[Unit]("Check physical bodies, resource lifetimes and host reports")

libraryDependencies += "io.7mind.izumi" %% "distage-testkit-runner" % sys.props("izumi.fixture.version") % Test
libraryDependencies ++= Seq("org.typelevel" %% "cats-effect" % "3.7.1", "dev.zio" %% "zio" % "2.1.26" excludeAll("dev.zio" %% "izumi-reflect"))
libraryDependencies ++= { if (scalaVersion.value.startsWith("3.")) Seq.empty else Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.4" cross CrossVersion.full)) }
scalacOptions ++= {
  if (scalaVersion.value.startsWith("3.")) Seq("-release:17", "-Ybackend-parallelism", "1", "-Yretain-trees", "-Xmax-inlines:64", "-Wunused:all", "-Xkind-projector:underscores")
  else Seq("-release:17", "-Xsource:3", "-P:kind-projector:underscore-placeholders")
}
Test / testFrameworks := Seq(new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"))
Test / testOptions += Tests.Argument(
  new TestFramework("izumi.distage.testkit.runner.bootstrap.Framework"),
  "--build-id", "published-target-bootstrap-host",
  "--target-id", "target-bootstrap-jvm",
  "--catalogue-id", "five-suite-catalogue",
)
Test / javaOptions += "-Dizumi.fixture.audit-root=" + file(sys.props("izumi.fixture.audit-root")).getAbsolutePath

prepareFixture := {
  val parsed = spaceDelimited("case name").parsed
  require(parsed.size == 1, "Expected exactly one case name")
  val audit = file(sys.props("izumi.fixture.audit-root"))
  require(audit.getCanonicalFile == (baseDirectory.value / "target/body-audit").getCanonicalFile, "Only the owned fixture audit directory may be cleared")
  IO.delete(audit)
  IO.createDirectory(audit)
  IO.delete(target.value / "test-reports")
  streams.value.log.info("TARGET_BOOTSTRAP_PREPARED case=" + parsed.head)
}

verifyFixture := {
  val parsed = spaceDelimited("case acquisitions suite labels").parsed
  require(parsed.size >= 3, "Expected case, acquisition count and suite labels")
  val caseName = parsed.head
  val expectedAcquisitions = parsed(1).toInt
  val labels = parsed.drop(2)
  require(labels.distinct.size == labels.size, "Expected unique suite labels")
  val prefix = "izumi.fixtures.host."
  val expectedBodies = labels.flatMap(label => (1 to 3).map(index => (prefix + label, index.toString))).sorted
  val audit = file(sys.props("izumi.fixture.audit-root"))
  val records = (audit * "*.body").get().sorted
  val bodies = records.map(file => IO.read(file).trim.split("\t", -1).toSeq)
  require(bodies.forall(_.size == 3), "Malformed physical body record")
  require(bodies.map(row => row(0) -> row(1)).sorted == expectedBodies, "Physical body identities differ from selected identities")
  val acquired = (audit * "*.acquire").get().map(file => IO.read(file).trim).sorted
  val released = (audit * "*.release").get().map(file => IO.read(file).trim).sorted
  require(acquired.size == expectedAcquisitions && acquired.distinct.size == acquired.size && acquired == released, "Physical acquisition/release records differ: " + acquired + "/" + released)
  val diLabels = Set("SuiteC", "SuiteD", "SuiteE")
  val diBodies = bodies.filter(row => diLabels.contains(row(0).stripPrefix(prefix)))
  require(bodies.filterNot(diBodies.contains).forall(_(2) == "plain"), "Plain or foreign body acquired a DI resource")
  require(diBodies.forall(row => acquired.contains(row(2))), "A DI body used an unrecorded resource")
  require(diBodies.map(_(2)).distinct.sorted == acquired, "Recorded DI lifetimes differ from bodies actually served")
  if (expectedAcquisitions == 1) require(diBodies.map(_(2)).distinct.size == 1, "Compatible selected DI suites did not share one resource")
  if (caseName == "fork-two-groups") {
    val left = diBodies.filter(_(0) == prefix + "SuiteC").map(_(2)).distinct
    val right = diBodies.filter(row => row(0) == prefix + "SuiteD" || row(0) == prefix + "SuiteE").map(_(2)).distinct
    require(left.size == 1 && right.size == 1 && left != right, "Explicit fork groups did not retain their independent resource scope")
  }
  val reports = (target.value / "test-reports" * "*.xml").get()
  val factory = DocumentBuilderFactory.newInstance()
  val cases = reports.flatMap { report =>
    val document = factory.newDocumentBuilder().parse(report)
    require(document.getElementsByTagName("failure").getLength == 0 && document.getElementsByTagName("error").getLength == 0 && document.getElementsByTagName("skipped").getLength == 0, "Host report contains failure: " + report)
    val nodes = document.getElementsByTagName("testcase")
    (0 until nodes.getLength).map { index =>
      val node = nodes.item(index).asInstanceOf[org.w3c.dom.Element]
      node.getAttribute("classname") -> node.getAttribute("name")
    }
  }
  val paths = Seq("equal display name should first", "equal display name should second", "equal display name should third")
  val expectedCases = labels.flatMap(label => paths.map(name => (prefix + label) -> name)).sorted
  require(cases.sorted == expectedCases, "Host report identities differ: " + cases.sorted + " expected " + expectedCases)
  val capture = file(sys.props("izumi.fixture.captures")) / caseName
  require(!capture.exists(), "Fixture capture must be new")
  IO.copyDirectory(audit, capture / "body-audit")
  IO.copyDirectory(target.value / "test-reports", capture / "test-reports")
  streams.value.log.info("TARGET_BOOTSTRAP_HOST_OK case=" + caseName + " suites=" + labels.size + " bodies=" + bodies.size + " reported=" + cases.size + " acquired=" + acquired.size + " released=" + released.size)
}
