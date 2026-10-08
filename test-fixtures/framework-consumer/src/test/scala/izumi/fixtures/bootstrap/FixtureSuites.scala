package izumi.fixtures.bootstrap

import izumi.distage.testkit.runner.spec.AsyncWordSpec

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.Future

final class BodyAudit(directory: Path, suite: String) {
  def record(index: Int): Unit = {
    val file = directory.resolve(suite + "-" + index + ".body")
    val _ = Files.write(file, (suite + "\t" + index).getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
  }
}

abstract class FixtureSuite extends AsyncWordSpec {
  private val audit = new BodyAudit(Paths.get(sys.props("izumi.fixture.audit-root")), getClass.getName)
  private val syncCount = new AtomicInteger(0)
  private val futureCount = new AtomicInteger(0)
  private val finalCount = new AtomicInteger(0)

  "equal display name" should {
    "sync" in {
      assert(syncCount.incrementAndGet() == 1)
      audit.record(1)
    }
    "future" in Future {
      assert(futureCount.incrementAndGet() == 1)
      audit.record(2)
    }
    "nested" should {
      "final" in {
        assert(finalCount.incrementAndGet() == 1)
        audit.record(3)
      }
    }
  }
}

final class SuiteA extends FixtureSuite
final class SuiteB extends FixtureSuite
final class SuiteC extends FixtureSuite
final class SuiteD extends FixtureSuite
final class SuiteE extends FixtureSuite
