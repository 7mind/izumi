package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.il.ast.DomainDefinition

trait ModelLoader {
  def load(): Seq[DomainDefinition]
}
