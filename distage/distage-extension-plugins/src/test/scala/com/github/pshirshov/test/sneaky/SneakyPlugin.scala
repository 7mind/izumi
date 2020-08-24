package com.github.pshirshov.test.sneaky

import izumi.distage.model.definition.ModuleDef
import izumi.distage.plugins.PluginBase
import izumi.fundamentals.platform.build.ExposedTestScope

/**
  * This is just to verify that plugin enumerator picks up transitively inherited plugins
  */
@ExposedTestScope
trait SneakyPlugin extends PluginBase with ModuleDef
