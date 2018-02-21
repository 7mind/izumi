package com.github.pshirshov.izumi.idealingua.model.il

import com.github.pshirshov.izumi.idealingua.model.common
import com.github.pshirshov.izumi.idealingua.model.common.TypeId

case class DomainId(pkg: common.Package, id: String) {
  def toPackage: common.Package = pkg :+ id
  def contains(typeId: TypeId): Boolean = {
    pkg.zip(typeId.pkg).map(_._1) == pkg
  }
}
