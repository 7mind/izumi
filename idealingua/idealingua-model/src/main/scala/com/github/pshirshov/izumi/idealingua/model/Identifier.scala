package com.github.pshirshov.izumi.idealingua.model

trait IDLGenerated {

}

trait Identifier {
  this: IDLGenerated =>
}

object Identifier {
  // TODO: here we should escape colons
  def escape(s: String): String = s
  def unescape(s: String): String = s
}
