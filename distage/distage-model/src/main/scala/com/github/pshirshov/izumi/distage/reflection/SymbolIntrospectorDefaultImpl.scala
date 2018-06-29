package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.distage.model.reflection.universe.DIUniverse
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

trait SymbolIntrospectorDefaultImpl extends SymbolIntrospector {

  override def selectConstructor(symb: u.TypeFull): SelectedConstructor = {
    val selectedConstructor = selectConstructorMethod(symb)
    val paramLists = selectedConstructor.typeSignatureIn(symb.tpe).paramLists
    SelectedConstructor(selectedConstructor, paramLists)
  }

  override def selectConstructorMethod(tpe: u.TypeFull): u.MethodSymb = {
    tpe.tpe.decl(u.u.termNames.CONSTRUCTOR).asTerm.alternatives.head.asMethod
  }

  override def selectNonImplicitParameters(symb: u.MethodSymb): List[List[u.Symb]] = {
    symb.paramLists.takeWhile(_.headOption.forall(!_.isImplicit))
  }

  override def isConcrete(symb: u.TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && !symb.tpe.typeSymbol.isAbstract
  }

  override def isWireableAbstract(symb: u.TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && symb.tpe.typeSymbol.isAbstract && symb.tpe.members.filter(_.isAbstract).forall(m => isWireableMethod(symb, m))
  }

  override def isFactory(symb: u.TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && symb.tpe.typeSymbol.isAbstract && {
      val abstracts = symb.tpe.members.filter(_.isAbstract)
      abstracts.exists(isFactoryMethod(symb, _)) &&
        abstracts.forall(m => isFactoryMethod(symb, m) || isWireableMethod(symb, m))
    }
  }

  override def isWireableMethod(tpe: u.TypeFull, decl: u.Symb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      decl.asMethod.paramLists.isEmpty && u.SafeType(decl.asMethod.returnType) != tpe
    }
  }

  override def isFactoryMethod(tpe: u.TypeFull, decl: u.Symb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      val paramLists = decl.asMethod.paramLists
      paramLists.nonEmpty && paramLists.forall(list => !list.contains(decl.asMethod.returnType) && !list.contains(tpe))
    }
  }

  override def findSymbolAnnotation(annType: u.TypeFull, symb: u.SymbolInfo): Option[u.u.Annotation] =
    symb.findAnnotation(annType)

  override def findTypeAnnotation(annType: u.TypeFull, tpe: u.TypeFull): Option[u.u.Annotation] = {
    val univ: u.u.type = u.u // intellij
    AnnotationTools.findTypeAnnotation(univ)(annType.tpe, tpe.tpe)
  }
}

object SymbolIntrospectorDefaultImpl {

  class Runtime
    extends SymbolIntrospector.Runtime
       with SymbolIntrospectorDefaultImpl

  object Static {
    def apply(macroUniverse: DIUniverse): SymbolIntrospector.Static[macroUniverse.type] =
      new SymbolIntrospectorDefaultImpl {
        override val u: macroUniverse.type = macroUniverse
      }
  }

}
