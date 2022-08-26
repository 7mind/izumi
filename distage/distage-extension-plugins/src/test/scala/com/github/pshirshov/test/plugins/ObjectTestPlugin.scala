package com.github.pshirshov.test.plugins

import com.github.pshirshov.test.sneaky.SneakyPlugin

object ObjectTestPlugin extends SneakyPlugin() {
  make[TestDep3]
}
