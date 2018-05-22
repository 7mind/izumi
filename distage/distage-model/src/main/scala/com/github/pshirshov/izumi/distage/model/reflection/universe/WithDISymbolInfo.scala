package com.github.pshirshov.izumi.distage.model.reflection.universe

import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

import scala.language.implicitConversions

trait WithDISymbolInfo {
  this: DIUniverseBase
    with WithDISafeType =>

  trait SymbolInfo {
    def name: String
    def tpe: TypeFull
    def annotations: List[u.Annotation]
    def isMethodSymbol: Boolean
  }

  object SymbolInfo {
    implicit def apply(symb: Symb): SymbolInfo = new SymbolInfo {
      override def name: String = symb.name.toString
      override def tpe: TypeFull = SafeType(symb.info.resultType)
      override def annotations: List[u.Annotation] = AnnotationTools.getAllAnnotations(u: u.type)(symb)
      override def isMethodSymbol: Boolean = symb.isMethod
    }

    def apply(name: String, tpe: TypeFull, annotations: List[u.Annotation], isMethodSymbol: Boolean): SymbolInfo = {
      final class Impl(val name: String = name, val tpe: TypeFull = tpe, val annotations: List[u.Annotation] = annotations, val isMethodSymbol: Boolean = isMethodSymbol) extends SymbolInfo
      new Impl()
    }
  }
}
