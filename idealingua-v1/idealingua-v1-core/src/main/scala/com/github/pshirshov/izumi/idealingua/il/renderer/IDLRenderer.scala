package com.github.pshirshov.izumi.idealingua.il.renderer

import com.github.pshirshov.izumi.idealingua.model.il.ast.typed._


final case class IDLRenderingOptions(expandIncludes: Boolean)

class IDLRenderer(defn: DomainDefinition, options: IDLRenderingOptions) {

  private val context = new IDLRenderingContext(defn, options)

  def render(): String = {
    import context._
    defn.render()
  }
}
