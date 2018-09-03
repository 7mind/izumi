package com.github.pshirshov.izumi.idealingua.translator.toscala.types.runtime

import com.github.pshirshov.izumi.idealingua.model.common.DomainId
import com.github.pshirshov.izumi.idealingua.translator.toscala.types.ScalaTypeConverter

import scala.reflect.{ClassTag, classTag}


final case class Pkg private(pkgParts: Seq[String]) {
  final val conv = new ScalaTypeConverter(DomainId(pkgParts.init, pkgParts.last))

  def within(name: String) = Pkg(pkgParts :+ name)

  def `import`: Import = Import.AllPackage(this)
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
