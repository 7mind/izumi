package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.WithReflection.isWireableMethod
import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.Association

import scala.reflect.runtime.universe


trait WithReflection {
  protected def symbolDeps(symbl: Symb): Seq[Association] = {
    // TODO: use extractors
    symbl match {
      case ConcreteSymbol(symb) =>
        val constructors = symb.info.decls.filter(_.isConstructor)
        // TODO: list should not be empty (?) and should has only one element (?)
        val selectedConstructor = constructors.head

        val paramLists = selectedConstructor.info.paramLists
        // TODO: param list should not be empty (?), what to do with multiple lists?..
        val selectedParamList = paramLists.head

        parametersToMaterials(selectedParamList)

      case AbstractSymbol(symb) =>
        // empty paramLists means parameterless method, List(List()) means nullarg method()
        val declaredAbstractMethods = symb.info.decls.filter(d => isWireableMethod(symb, d))

        // TODO: here we should handle annotations/etc
        declaredAbstractMethods.map(m => Association.Method(m, DIKey.TypeKey(m.info.resultType.typeSymbol))).toSeq


      case FactorySymbol(_, factoryMethods) =>
        factoryMethods.flatMap {
          m =>
            val paramLists = m.asMethod.info.paramLists
            val selectedParamList = paramLists.head
            
            val unrequiredMaterials = parametersToMaterials(selectedParamList).toSet
            val allDeps = symbolDeps(m.asMethod.returnType.typeSymbol)
            val filtered = allDeps.filterNot(d => unrequiredMaterials.exists(m => d.wireWith.symbol == m.wireWith.symbol))

            filtered
        }

      case _ =>
        Seq()
    }

  }


  private def parametersToMaterials(selectedParamList: List[universe.Symbol]): List[Association] = {
    selectedParamList.map {
      parameter =>
        // TODO: here we should handle annotations/etc
        Association.Parameter(parameter, DIKey.TypeKey(parameter.info.typeSymbol))
    }
  }
}

object WithReflection {
  def isConcrete(symb: Symb): Boolean = {
    symb.isClass && !symb.isAbstract
  }

  def isWireableAbstract(symb: Symb): Boolean = {
    symb.isClass && symb.isAbstract && symb.info.decls.filter(_.isAbstract).forall(m => isWireableMethod(symb, m))
  }

  def isFactory(symb: Symb): Boolean = {
    symb.isClass && symb.isAbstract && symb.info.decls.filter(_.isAbstract).forall(m => isFactoryMethod(symb, m) || isWireableMethod(symb, m))
  }

  private def isWireableMethod(tpe: Symb, decl: Symb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      decl.info.paramLists.isEmpty && decl.asMethod.returnType.typeSymbol != tpe
    }
  }

  def isFactoryMethod(tpe: Symb, decl: Symb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      val paramLists = decl.info.paramLists
      paramLists.nonEmpty && paramLists.forall(list => !list.contains(decl.asMethod.returnType) && !list.contains(tpe))
    }
  }
}