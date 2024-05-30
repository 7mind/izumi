package com.github.pshirshov.test.sneaky

import izumi.distage.model.definition.ModuleDef
import izumi.distage.plugins.{ForcedRecompilationToken, PluginBase}

/**
  * This is just to verify that plugin enumerator picks up transitively inherited plugins
  */
abstract class SneakyPlugin(implicit val ev: ForcedRecompilationToken[?]) extends PluginBase with ModuleDef
