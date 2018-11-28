package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.il.loader.model.{LoadedModels, UnresolvedDomains}

trait ModelResolver {
  def resolve(domains: UnresolvedDomains): LoadedModels
}
