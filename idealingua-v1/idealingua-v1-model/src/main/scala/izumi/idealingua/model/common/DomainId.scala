package izumi.idealingua.model.common

import izumi.idealingua.model.common

final case class DomainId(pkg: common.Package, id: String) {
  override def toString: String = s"{${toPackage.mkString(".")}}"

  def toPackage: common.Package = pkg :+ id
}

object DomainId {
  final val Builtin = DomainId(Seq.empty, "/")
  final val Undefined = DomainId(Seq.empty, ".")
}



