package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.loader.UnresolvedDomains


trait ModelLoader {
  def load(): UnresolvedDomains
}


object ModelLoader {
  final val overlayVirtualDir = "[overlays]"
}
