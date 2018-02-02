package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.reflection.SelectedConstructor
import com.github.pshirshov.izumi.fundamentals.reflection._
import com.github.pshirshov.izumi.fundamentals.reflection.EqualitySafeType

import scala.reflect.runtime.universe

class SymbolIntrospectorDefaultImpl extends SymbolIntrospector {
  override def selectConstructor(symb: TypeFull): SelectedConstructor = {
    // val constructors = symb.symbol.members.filter(_.isConstructor)
    // TODO: list should not be empty (?) and should has only one element (?)

    // TODO: only considers scala constructors
    val selectedConstructor = symb.tpe.decl(universe.termNames.CONSTRUCTOR).asMethod.typeSignatureIn(symb.tpe)

    val paramLists = selectedConstructor.paramLists
    // TODO: param list should not be empty (?), what to do with multiple lists?..
    SelectedConstructor(selectedConstructor, paramLists.head)
  }

  override def selectParameters(symb: MethodSymb): List[TypeSymb] = {
    symb.paramLists.head
  }

  override def isConcrete(symb: TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && !symb.tpe.typeSymbol.isAbstract
  }

  override def isWireableAbstract(symb: TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && symb.tpe.typeSymbol.isAbstract && symb.tpe.members.filter(_.isAbstract).forall(m => isWireableMethod(symb, m))
  }

  override def isFactory(symb: TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && symb.tpe.typeSymbol.isAbstract && {
      val abstracts = symb.tpe.members.filter(_.isAbstract)
      abstracts.exists(isFactoryMethod(symb, _)) &&
        abstracts.forall(m => isFactoryMethod(symb, m) || isWireableMethod(symb, m))
    }
  }

  override def isWireableMethod(tpe: TypeFull, decl: TypeSymb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      decl.asMethod.paramLists.isEmpty && EqualitySafeType(decl.asMethod.returnType) != tpe
    }
  }

  override def isFactoryMethod(tpe: TypeFull, decl: TypeSymb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      val paramLists = decl.asMethod.paramLists
      paramLists.nonEmpty && paramLists.forall(list => !list.contains(decl.asMethod.returnType) && !list.contains(tpe))
    }
  }
}

object SymbolIntrospectorDefaultImpl {
  final val instance = new SymbolIntrospectorDefaultImpl()
}