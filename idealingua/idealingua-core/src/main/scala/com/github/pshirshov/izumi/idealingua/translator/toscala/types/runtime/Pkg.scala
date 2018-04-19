package com.github.pshirshov.izumi.idealingua.translator.toscala.types.runtime

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaTypeConverter

import scala.reflect._

case class Pkg private(pkgParts: Seq[String]) {
  final val conv = new ScalaTypeConverter(DomainId(pkgParts.init, pkgParts.last))

  def `import`: Import = Import.of(this)
}

case class Import private(parts: Seq[String]) {
  def render: String = s"import _root_.${parts.mkString(".")}"
}

object Import {
  def of(pkg: Pkg): Import = from(pkg, "_")

  def from(pkg: Pkg, symbol: String): Import = Import(pkg.pkgParts :+ symbol)

}

object Pkg {
  def parentOf[T: ClassTag]: Pkg = {
    val classPkg = classTag[T].runtimeClass.getPackage.getName
    val classPkgParts = classPkg.split('.').toSeq
    Pkg(classPkgParts)
  }

  def of[T: ClassTag]: Pkg = {
    val classPkg = classTag[T].runtimeClass.getPackage.getName
    val classPkgParts = classPkg.split('.')
    Pkg(classPkgParts)
  }

  def language: Pkg = Pkg(Seq("scala", "language"))
}
