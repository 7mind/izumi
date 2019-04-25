package com.github.pshirshov.test.sneaky

import com.github.pshirshov.izumi.distage.plugins.PluginDef
import com.github.pshirshov.izumi.fundamentals.platform.build.ExposedTestScope

/**
  * This is just to verify that plugin enumerator picks up transitively inherited plugins
  */
@ExposedTestScope
trait SneakyPlugin extends PluginDef
