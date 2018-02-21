package com.github.pshirshov.izumi.idealingua.model.finaldef

import com.github.pshirshov.izumi.idealingua.model.common

case class DomainId(pkg: common.Package, id: String) {
  def toPackage: common.Package = pkg :+ id
}
