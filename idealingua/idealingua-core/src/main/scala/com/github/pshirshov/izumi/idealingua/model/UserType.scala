package com.github.pshirshov.izumi.idealingua.model

case class UserType(pkg: Package, name: TypeName) extends TypeId

object UserType {
  def parse(s: String): UserType = {
    val parts = s.split('.')
    UserType(parts.toSeq.init, TypeName(parts.last))
  }
}
