package com.github.pshirshov.izumi.idealingua.model.il.ast.typed

import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.{AbstractTypeId, Builtin}

case class DomainId(pkg: common.Package, id: String) {
  override def toString: String = s"::${toPackage.mkString(".")}"

  def toPackage: common.Package = pkg :+ id

  def contains(typeId: AbstractTypeId): Boolean = {
    typeId match {
      case _: Builtin =>
        false
      case t if t.pkg.isEmpty =>
        true
      case t =>
        toPackage.zip(t.pkg).forall(ab =>  ab._1 == ab._2)
    }
  }

  def toDomainId(typeId: AbstractTypeId): DomainId = {
    typeId match {
      case t if contains(t) =>
        this

      case t =>
        DomainId(t.pkg.init, t.pkg.last)
    }
  }
}
