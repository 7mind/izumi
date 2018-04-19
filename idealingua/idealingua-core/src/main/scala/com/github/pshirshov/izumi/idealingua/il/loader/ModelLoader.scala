package com.github.pshirshov.izumi.idealingua.il.loader

import com.github.pshirshov.izumi.idealingua.model.typespace.Typespace

trait ModelLoader {
  def load(): Seq[Typespace]
}
