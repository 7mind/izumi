package izumi.distage.testkit.st.fixtures

import java.util.concurrent.atomic.AtomicInteger

import izumi.distage.config.annotations.ConfPath
import izumi.distage.model.definition.DIResource
import izumi.distage.model.definition.StandardAxis._
import izumi.distage.monadic.modules.{CatsDIEffectModule, ZIODIEffectModule}
import izumi.distage.plugins.PluginDef
import izumi.distage.roles.model.IntegrationCheck
import izumi.fundamentals.platform.integration.ResourceCheck
import izumi.fundamentals.platform.language.Quirks
import izumi.logstage.api.IzLogger

import scala.collection.mutable

class SelftestCounters {
  val closedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
}

class TestResourceDI() extends AutoCloseable {
  override def close(): Unit = {
    Quirks.discard(TestResourceDI.closeCount.incrementAndGet())
  }
}

object TestResourceDI {
  final val closeCount = new AtomicInteger()
}

class TestResource1(counter: SelftestCounters, logger: IzLogger) extends AutoCloseable {
  override def close(): Unit = counter.closedCloseables += {
    logger.info(s"[test] Closing $this")
    this
  }
}

class TestResource2(val testResource1: TestResource1, counter: SelftestCounters, logger: IzLogger) extends AutoCloseable {
  override def close(): Unit = {
    logger.info(s"[test] Closing $this")
    counter.closedCloseables += this
  }
}

class TestService1(val testResource2: TestResource2, val testComponent3: TestComponent3, counter: SelftestCounters, logger: IzLogger) extends AutoCloseable {
  override def close(): Unit = {
    logger.info(s"[test] Closing $this")
    counter.closedCloseables += this
  }
}

case class TestComponent1(counter: SelftestCounters, logger: IzLogger)

case class TestComponent2(testComponent1: TestComponent1, counter: SelftestCounters, logger: IzLogger)

case class TestComponent3(testComponent2: TestComponent2, counter: SelftestCounters, logger: IzLogger)

class TestFailingIntegrationResource extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck =
    ResourceCheck.ResourceUnavailable("Resource check test", None)
}

case class TestConfig(provided: Int, overriden: Int)

case class TestConfig1(x: Int, y: Int)

class TestService2(
  @ConfPath("test") val cfg: TestConfig,
  @ConfPath("missing-test-section") val cfg1: TestConfig
)

trait Conflict
case class Conflict1() extends Conflict
case class Conflict2(u: UnsolvableConflict) extends Conflict

trait UnsolvableConflict
class UnsolvableConflict1 extends UnsolvableConflict
class UnsolvableConflict2 extends UnsolvableConflict

class TestPlugin01 extends PluginDef {
  make[Conflict].tagged(Env.Test).from[Conflict1]
  make[Conflict].tagged(Env.Prod).from[Conflict2]
  make[UnsolvableConflict].from[UnsolvableConflict1]
  make[UnsolvableConflict].from[UnsolvableConflict2]
}

object MonadPlugin extends PluginDef with CatsDIEffectModule with ZIODIEffectModule

object TestPlugin00 extends PluginDef {
  make[TestService1]
  make[TestService2]
  make[TestResource1]
  make[TestResource2]
  make[SelftestCounters]
  make[TestComponent3]
  make[TestComponent2]
  make[TestComponent1]
  make[TestFailingIntegrationResource]
  make[TestResourceDI].fromResource(DIResource.fromAutoCloseable(new TestResourceDI()))

}
