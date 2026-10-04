package izumi.fixtures.host

import distage.DIKey
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.model.{TestActivationStrategy, TestConfig}
import izumi.distage.testkit.runner.spec.{AnyWordSpec, SpecIdentity}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}

final class BodyAudit(directory: Path, suite: String) {
  def record(index: Int, resource: Option[String]): Unit = {
    val file = directory.resolve(suite + "-" + index + ".body")
    val data = suite + "\t" + index + "\t" + resource.getOrElse("plain")
    val _ = Files.write(file, data.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
  }
}

abstract class PlainFixtureSuite extends AnyWordSpec {
  private val audit = new BodyAudit(Paths.get(sys.props("izumi.fixture.audit-root")), getClass.getName)
  "equal display name" should {
    "first" in audit.record(1, None)
    "second" in audit.record(2, None)
    "third" in audit.record(3, None)
  }
}

abstract class DIFixtureSuite extends SpecIdentity {
  private val audit = new BodyAudit(Paths.get(sys.props("izumi.fixture.audit-root")), getClass.getName)
  override protected def config: TestConfig = TestConfig.empty.copy(
    pluginConfig = PluginConfig.cached("izumi.fixtures.host.plugins"),
    memoizationRoots = TestConfig.PriorityAxisDIKeys.fromSet(Set(DIKey[SharedResource])),
    activationStrategy = TestActivationStrategy.IgnoreConfig,
  )
  "equal display name" should {
    "first" in { (resource: SharedResource) => audit.record(1, Some(resource.id)) }
    "second" in { (resource: SharedResource) => audit.record(2, Some(resource.id)) }
    "third" in { (resource: SharedResource) => audit.record(3, Some(resource.id)) }
  }
}

final class SharedResource(val id: String, val directory: Path)
final class SuiteA extends PlainFixtureSuite
final class SuiteB extends PlainFixtureSuite
final class SuiteC extends DIFixtureSuite
final class SuiteD extends DIFixtureSuite
final class SuiteE extends DIFixtureSuite
