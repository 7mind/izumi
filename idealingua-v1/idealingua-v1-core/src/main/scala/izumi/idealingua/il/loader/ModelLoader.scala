package izumi.idealingua.il.loader

import izumi.idealingua.model.loader.UnresolvedDomains

trait ModelLoader {
  def load(): UnresolvedDomains
}

object ModelLoader {
  final val overlayVirtualDir = "[overlays]"
}
