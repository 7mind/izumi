package com.github.abc

import com.github.pshirshov.izumi.distage.plugins.PluginDef

// Classgraph will not resolve a plugin if it's inherited from this, not from PLuginDef directly
// FIXME: report issue
trait SneakyPlugin extends PluginDef
