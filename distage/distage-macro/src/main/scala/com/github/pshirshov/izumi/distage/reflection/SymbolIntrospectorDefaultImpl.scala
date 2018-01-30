package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.reflection.SymbolIntrospector
import com.github.pshirshov.izumi.distage.model.reflection.universe.MacroUniverse

trait SymbolIntrospectorDefaultImpl extends SymbolIntrospector {

  override def selectConstructor(symb: u.TypeFull): u.SelectedConstructor = {
    // val constructors = symb.symbol.members.filter(_.isConstructor)
    // TODO: list should not be empty (?) and should has only one element (?)
    val selectedConstructor = symb.tpe.decl(u.u.termNames.CONSTRUCTOR).asMethod
    // TODO: param list should not be empty (?), what to do with multiple lists?..
    // TODO: what to do with implicits
    val paramLists = selectedConstructor.typeSignatureIn(symb.tpe).paramLists.flatten
    u.SelectedConstructor(selectedConstructor, paramLists)
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

  class Java
    extends SymbolIntrospector.Java
       with SymbolIntrospectorDefaultImpl
  object Java {
    final val instance = new SymbolIntrospectorDefaultImpl.Java
  }

  class Macro[M <: MacroUniverse[_]](macroUniverse: M)
    extends SymbolIntrospector.Macro[M](macroUniverse)
       with SymbolIntrospectorDefaultImpl
  object Macro {
    def instance[M <: MacroUniverse[_]](macroUniverse: M): SymbolIntrospector.Macro[macroUniverse.type] =
      new SymbolIntrospectorDefaultImpl.Macro(macroUniverse)
  }

}
