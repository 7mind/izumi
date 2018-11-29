package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.loader.{LoadedModels, UnresolvedDomains}

trait ModelResolver {
  def resolve(domains: UnresolvedDomains): LoadedModels
}
