package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.distage.model.reflection.universe.DIUniverse
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

trait SymbolIntrospectorDefaultImpl extends SymbolIntrospector {

  override def selectConstructor(symb: u.SafeType): SelectedConstructor = {
    val selectedConstructor = selectConstructorMethod(symb)
    val originalParamListTypes = selectedConstructor.paramLists.map(_.map(_.typeSignature))
    val paramLists = selectedConstructor.typeSignatureIn(symb.tpe).paramLists
    // Hack due to .typeSignatureIn throwing out type annotations...
    val paramsWithAnnos = originalParamListTypes
      .zip(paramLists)
      .map {
        case (origTypes, params) =>
          origTypes.zip(params).map {
            case (o: u.u.AnnotatedTypeApi, p) =>
              u.SymbolInfo.Runtime(p, symb, o.annotations)
            case (_, p) =>
              u.SymbolInfo.Runtime(p, symb)
          }
      }
    SelectedConstructor(selectedConstructor, paramsWithAnnos)
  }

  override def selectConstructorMethod(tpe: u.SafeType): u.MethodSymb = {
    tpe.tpe.decl(u.u.termNames.CONSTRUCTOR).asTerm.alternatives.head.asMethod
  }

  override def selectNonImplicitParameters(symb: u.MethodSymb): List[List[u.Symb]] = {
    symb.paramLists.takeWhile(_.headOption.forall(!_.isImplicit))
  }

  override def isConcrete(symb: u.SafeType): Boolean = {
    symb.tpe.typeSymbol.isClass && !symb.tpe.typeSymbol.isAbstract
  }

  override def isWireableAbstract(symb: u.SafeType): Boolean = {
    symb.tpe.typeSymbol.isClass && symb.tpe.typeSymbol.isAbstract && symb.tpe.members.filter(_.isAbstract).forall(m => isWireableMethod(symb, m))
  }

  override def isFactory(symb: u.SafeType): Boolean = {
    symb.tpe.typeSymbol.isClass && symb.tpe.typeSymbol.isAbstract && {
      val abstracts = symb.tpe.members.filter(_.isAbstract)
      abstracts.exists(isFactoryMethod(symb, _)) &&
        abstracts.forall(m => isFactoryMethod(symb, m) || isWireableMethod(symb, m))
    }
  }

  override def isWireableMethod(tpe: u.SafeType, decl: u.Symb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      decl.asMethod.paramLists.isEmpty && u.SafeType(decl.asMethod.returnType) != tpe
    }
  }

  override def isFactoryMethod(tpe: u.SafeType, decl: u.Symb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      val paramLists = decl.asMethod.paramLists
      paramLists.nonEmpty && paramLists.forall(list => !list.contains(decl.asMethod.returnType) && !list.contains(tpe))
    }
  }

  override def findSymbolAnnotation(annType: u.SafeType, symb: u.SymbolInfo): Option[u.u.Annotation] = {
    symb.findUniqueAnnotation(annType)
  }

  override def findTypeAnnotation(annType: u.SafeType, tpe: u.SafeType): Option[u.u.Annotation] = {
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
