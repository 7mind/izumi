package com.github.pshirshov.izumi.idealingua.model.common

final case class TypePath(domain: DomainId, within: Package) {
  def sub(v: TypeName) = TypePath(domain, within :+ v)

  def toPackage: Package= domain.toPackage ++ within

  override def toString: TypeName = {
    if (within.isEmpty) {
      s"$domain/"
    } else {
      s"$domain/${within.mkString("/")}"
    }
  }
}
