package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.distage.roles.roles.RoleComponent

import scala.collection.mutable

class InitCounter {
  val closedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
  val startedRoleComponents: mutable.ArrayBuffer[RoleComponent] = mutable.ArrayBuffer()
  val closedRoleComponents: mutable.ArrayBuffer[RoleComponent] = mutable.ArrayBuffer()
}

class TestResource1(counter: InitCounter) extends AutoCloseable {
  override def close(): Unit = counter.closedCloseables += this
}

class TestResource2(val testResource1: TestResource1, counter: InitCounter) extends AutoCloseable {
  override def close(): Unit = counter.closedCloseables += this
}

class TestService1(val testResource2: TestResource2, val testComponent3: TestComponent3, counter: InitCounter) extends AutoCloseable {
  override def close(): Unit = counter.closedCloseables += this
}

class TestComponent1(counter: InitCounter) extends RoleComponent {
  override def start(): Unit = counter.startedRoleComponents += this
  override def stop(): Unit = counter.closedRoleComponents += this
}

class TestComponent2(val testComponent1: TestComponent1, counter: InitCounter) extends RoleComponent {
  override def start(): Unit = counter.startedRoleComponents += this
  override def stop(): Unit = counter.closedRoleComponents += this
}

class TestComponent3(val testComponent2: TestComponent2, counter: InitCounter) extends RoleComponent {
  override def start(): Unit = counter.startedRoleComponents += this
  override def stop(): Unit = counter.closedRoleComponents += this
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
}
