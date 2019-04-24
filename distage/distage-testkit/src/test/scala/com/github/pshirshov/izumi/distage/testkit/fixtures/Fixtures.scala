package com.github.pshirshov.izumi.distage.testkit.fixtures

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadPoolExecutor}

import cats.effect.IO
import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.model.definition.{DIResource, ModuleDef}
import com.github.pshirshov.izumi.distage.model.monadic.{DIEffect, DIEffectRunner}
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.model.IntegrationCheck
import com.github.pshirshov.izumi.distage.roles.services.ResourceRewriter
import com.github.pshirshov.izumi.functional.bio.BIORunner
import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck
import com.github.pshirshov.izumi.fundamentals.platform.language.Quirks
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.Id
import scalaz.zio

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
                    @ConfPath("test") val cfg: TestConfig
                    , @ConfPath("missing-test-section") val cfg1: TestConfig
                  )

class TestPlugin
  extends PluginDef
    with CatsDIEffectModule
    with ZioDIEffectModule {
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


trait CatsDIEffectModule extends ModuleDef {
  addImplicit[DIEffectRunner[IO]]
  addImplicit[DIEffect[IO]]
}

trait ZioDIEffectModule extends ModuleDef {
  make[DIEffectRunner[scalaz.zio.IO[Throwable, ?]]].from[DIEffectRunner.BIOImpl[scalaz.zio.IO]]
  addImplicit[DIEffect[scalaz.zio.IO[Throwable, ?]]]

  make[ThreadPoolExecutor].named("zio.pool.cpu")
    .fromResource {
      logger: IzLogger =>
        ResourceRewriter.fromExecutorService(logger, Executors.newFixedThreadPool(8).asInstanceOf[ThreadPoolExecutor])
    }

  make[ThreadPoolExecutor].named("zio.pool.io")
    .fromResource {
      logger: IzLogger =>
        ResourceRewriter.fromExecutorService(logger, Executors.newCachedThreadPool().asInstanceOf[ThreadPoolExecutor])
    }

  make[BIORunner[zio.IO]].from {
    (
      cpuPool: ThreadPoolExecutor@Id("zio.pool.cpu"),
      blockingPool: ThreadPoolExecutor@Id("zio.pool.io"),
      logger: IzLogger,
    ) =>
      val handler = BIORunner.DefaultHandler.Custom(message => zio.IO.sync(logger.warn(s"Fiber failed: $message")))

      BIORunner.createZIO(
        cpuPool
        , blockingPool
        , handler
      )
  }
}
