package com.github.pshirshov.izumi.fundamentals.reflection

import com.github.pshirshov.izumi.fundamentals.platform.jvm.SourcePackage

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

final case class SourcePackageMaterializer(get: SourcePackage)

object SourcePackageMaterializer {
  implicit def materialize: SourcePackageMaterializer = macro getSourcePackage

  def apply()(implicit ev: SourcePackageMaterializer): SourcePackageMaterializer = ev

  def thisPkg(implicit pkg: SourcePackageMaterializer): String = pkg.get.pkg

  // copypaste from lihaoyi 'sourcecode' https://github.com/lihaoyi/sourcecode/blob/d32637de59d5ecdd5040d1ef06a9370bc46a310f/sourcecode/shared/src/main/scala/sourcecode/SourceContext.scala
  def getSourcePackage(c: blackbox.Context): c.Expr[SourcePackageMaterializer] = {
    import c.universe._
    var current = c.internal.enclosingOwner
    var path = List.empty[String]
    while(current != NoSymbol && current.toString != "package <root>"){
      if (current.isPackage) {
        path = current.name.decodedName.toString.trim :: path
      }
      current = current.owner
    }
    val renderedPath = path.mkString(".")
    val pathExpr = c.Expr[String](q"${renderedPath}")

    reify {
      SourcePackageMaterializer(SourcePackage(pathExpr.splice))
    }
  }
}
