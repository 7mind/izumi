package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.distage.model.reflection.universe.StaticDIUniverse

trait SymbolIntrospectorDefaultImpl extends SymbolIntrospector {

  override def selectConstructor(symb: u.TypeFull): SelectedConstructor = {
    // val constructors = symb.symbol.members.filter(_.isConstructor)
    // TODO: list should not be empty (?) and should has only one element (?)
    val selectedConstructor = symb.tpe.decl(u.u.termNames.CONSTRUCTOR).asTerm.alternatives.head.asMethod
    // TODO: param list should not be empty (?), what to do with multiple lists?..
    // TODO: what to do with implicits
    val paramLists = selectedConstructor.typeSignatureIn(symb.tpe).paramLists.flatten
    SelectedConstructor(selectedConstructor, paramLists)
  }

  override def selectParameters(symb: u.MethodSymb): List[u.Symb] = {
    symb.paramLists.head
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
}

object SymbolIntrospectorDefaultImpl {

  class Runtime
    extends SymbolIntrospector.Runtime
       with SymbolIntrospectorDefaultImpl

  class Static[M <: StaticDIUniverse[_]](macroUniverse: M)
    extends SymbolIntrospector.Static[M](macroUniverse)
       with SymbolIntrospectorDefaultImpl

  object Static {
    def instance[M <: StaticDIUniverse[_]](macroUniverse: M): SymbolIntrospector.Static[macroUniverse.type] =
      new SymbolIntrospectorDefaultImpl.Static(macroUniverse)
  }

}
