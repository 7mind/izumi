package com.github.pshirshov.test.plugins

import com.github.pshirshov.test.sneaky.SneakyPlugin
import izumi.distage.plugins.ForcedRecompilationToken

object ObjectTestPlugin extends SneakyPlugin() {
  make[TestDep3]
}
