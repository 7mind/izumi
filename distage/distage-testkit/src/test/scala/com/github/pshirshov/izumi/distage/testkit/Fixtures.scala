package com.github.pshirshov.izumi.distage.testkit

import cats.effect.IO
import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.model.monadic.DIEffect
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.{DIEffectRunner, IntegrationCheck, RoleComponent}
import com.github.pshirshov.izumi.fundamentals.platform.integration.ResourceCheck
import com.github.pshirshov.izumi.logstage.api.IzLogger

import scala.collection.mutable

class InitCounter {
  val closedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
  val startedRoleComponents: mutable.ArrayBuffer[RoleComponent] = mutable.ArrayBuffer()
  val closedRoleComponents: mutable.ArrayBuffer[RoleComponent] = mutable.ArrayBuffer()
}

class TestResource1(counter: InitCounter, logger: IzLogger) extends AutoCloseable {
  override def close(): Unit = counter.closedCloseables += {
    logger.info(s"[test] Closing $this")
    this
  }
}

class TestResource2(val testResource1: TestResource1, counter: InitCounter, logger: IzLogger) extends AutoCloseable {
  override def close(): Unit = {
    logger.info(s"[test] Closing $this")
    counter.closedCloseables += this
  }
}

class TestService1(val testResource2: TestResource2, val testComponent3: TestComponent3, counter: InitCounter, logger: IzLogger) extends AutoCloseable {
  override def close(): Unit = {
    logger.info(s"[test] Closing $this")
    counter.closedCloseables += this
  }
}

class TestComponent1(counter: InitCounter, logger: IzLogger) extends RoleComponent {
  override def start(): Unit = {
    logger.info(s"[test] Starting $this")
    counter.startedRoleComponents += this
  }

  override def stop(): Unit = {
    logger.info(s"[test] Closing $this")
    counter.closedRoleComponents += this
  }
}

class TestComponent2(val testComponent1: TestComponent1, counter: InitCounter, logger: IzLogger) extends RoleComponent {
  override def start(): Unit = {
    logger.info(s"[test] Starting $this")
    assert(counter.startedRoleComponents.contains(testComponent1))
    counter.startedRoleComponents += this
  }

  override def stop(): Unit = {
    logger.info(s"[test] Closing $this")
    counter.closedRoleComponents += this
  }
}

class TestComponent3(val testComponent2: TestComponent2, counter: InitCounter, logger: IzLogger) extends RoleComponent {
  override def start(): Unit = {
    logger.info(s"[test] Starting $this")
    assert(counter.startedRoleComponents.contains(testComponent2))
    counter.startedRoleComponents += this
  }

  override def stop(): Unit = {
    logger.info(s"[test] Closing $this")
    counter.closedRoleComponents += this
  }
}

class TestFailingIntegrationResource extends IntegrationCheck {
  override def resourcesAvailable(): ResourceCheck =
    ResourceCheck.ResourceUnavailable("Fail!", Some(new RuntimeException("")))
}

case class TestConfig(x: Int, y: Int)

case class TestConfig1(x: Int, y: Int)

class TestService2(
                    @ConfPath("test") val cfg: TestConfig
                    , @ConfPath("test1") val cfg1: TestConfig
                  )

class TestPlugin extends PluginDef {
  make[TestService1]
  make[TestService2]
  make[TestResource1]
  make[TestResource2]
  make[InitCounter]
  make[TestComponent3]
  make[TestComponent2]
  make[TestComponent1]
  addImplicit[DIEffectRunner[IO]]
  addImplicit[DIEffect[IO]]
}
