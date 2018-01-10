package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.Association

import scala.reflect.runtime.universe

trait WithReflection {
  protected def symbolDeps(symb: Symb): Seq[Association] = {
    if (isConcrete(symb)) {
      val constructors = symb.info.decls.filter(_.isConstructor)
      // TODO: list should not be empty (?) and should has only one element (?)
      val selectedConstructor = constructors.head

      val paramLists = selectedConstructor.info.paramLists
      // TODO: param list should not be empty (?), what to do with multiple lists?..
      val selectedParamList = paramLists.head

      selectedParamList.map {
        parameter =>
          // TODO: here we should handle annotations/etc
          Association.Parameter(parameter, DIKey.TypeKey(parameter.info.typeSymbol))
      }
    } else {
      // empty paramLists means parameterless method, List(List()) means nullarg method()
      val declaredAbstractMethods = symb.info.decls.filter(d => isWireableMethod(d))

      // TODO: here we should handle annotations/etc
      declaredAbstractMethods.map(m => Association.Method(m, DIKey.TypeKey(m.info.resultType.typeSymbol))).toSeq
    }
  }


  protected def isConcrete(symb: Symb): Boolean = {
    symb.isClass && !symb.isAbstract
  }

  protected def isWireableAbstract(symb: Symb): Boolean = {
    symb.isClass && symb.isAbstract && symb.info.decls.filter(_.isAbstract).forall(isWireableMethod)
  }

  private def isWireableMethod(d: universe.Symbol): Boolean = {
    d.isMethod && d.isAbstract && !d.isSynthetic && d.info.paramLists.isEmpty
  }
}
