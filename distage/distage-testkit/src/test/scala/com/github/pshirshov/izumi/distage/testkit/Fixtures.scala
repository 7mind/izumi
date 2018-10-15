package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.plugins.PluginDef

import scala.collection.mutable

class InitCounter {
  val closedCloseables: mutable.ArrayBuffer[AutoCloseable] = mutable.ArrayBuffer()
}

class TestResource1(counter: InitCounter) extends AutoCloseable {
  override def close(): Unit = counter.closedCloseables += this
}

class TestResource2(val testResource1: TestResource1, counter: InitCounter) extends AutoCloseable {
  override def close(): Unit = counter.closedCloseables += this
}

class TestService1(val testResource2: TestResource2, counter: InitCounter) extends AutoCloseable {
  override def close(): Unit = counter.closedCloseables += this
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
}
