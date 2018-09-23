package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.config.annotations.ConfPath
import com.github.pshirshov.izumi.distage.plugins.PluginDef

class TestService1 extends AutoCloseable {
  override def close(): Unit = {
  }
}

case class TestConfig(x: Int, y: Int)

class TestService2(@ConfPath("test") val cfg: TestConfig) {
}

class TestPlugin extends PluginDef {
  make[TestService1]
  make[TestService2]
}
