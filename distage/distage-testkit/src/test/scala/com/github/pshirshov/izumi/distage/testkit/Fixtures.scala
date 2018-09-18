package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.plugins.PluginDef

class TestService1 extends AutoCloseable {
  override def close(): Unit = {
  }
}

class TestService2 {
}

class TestPlugin extends PluginDef {
  make[TestService1]
  make[TestService2]
}
