package com.github.pshirshov.izumi.idealingua.translator.toscala.extensions

import scala.meta.Defn

trait AbstractCogenProduct {
  def render: List[Defn]
}
