package com.github.pshirshov.test.sneaky

import izumi.distage.model.definition.ModuleDef
import izumi.distage.plugins.{ForcedRecompilationToken, PluginBase}
import izumi.fundamentals.platform.build.ExposedTestScope

/**
  * This is just to verify that plugin enumerator picks up transitively inherited plugins
  */
@ExposedTestScope
abstract class SneakyPlugin[T](implicit val ev: ForcedRecompilationToken[T]) extends PluginBase with ModuleDef
