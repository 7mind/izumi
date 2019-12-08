package com.github.pshirshov.test.sneaky

import izumi.distage.plugins.PluginDef
import izumi.fundamentals.platform.build.ExposedTestScope

/**
  * This is just to verify that plugin enumerator picks up transitively inherited plugins
  */
@ExposedTestScope
trait SneakyPlugin extends PluginDef
