package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.model.plan.Association
import org.bitbucket.pshirshov.izumi.di.{TypeFull, TypeSymb}


class ReflectionProviderDefaultImpl(keyProvider: DependencyKeyProvider) extends ReflectionProvider {

  override def symbolDeps(symbl: TypeFull): Seq[Association] = {
    symbl match {
      case ConcreteSymbol(symb) =>
        val constructors = symb.members.filter(_.isConstructor)
        // TODO: list should not be empty (?) and should has only one element (?)
        val selectedConstructor = constructors.head

        val paramLists = selectedConstructor.info.paramLists
        // TODO: param list should not be empty (?), what to do with multiple lists?..
        val selectedParamList = paramLists.head

        parametersToMaterials(selectedParamList)

      case AbstractSymbol(symb) =>
        // empty paramLists means parameterless method, List(List()) means nullarg method()
        val declaredAbstractMethods = symb.members.filter(d => isWireableMethod(symb, d))
        declaredAbstractMethods.map(m => Association.Method(m, keyProvider.keyFromMethod(m))).toSeq

      case FactorySymbol(_, factoryMethods) =>
        factoryMethods.flatMap {
          m =>
            val paramLists = m.asMethod.info.paramLists
            val selectedParamList = paramLists.head

            val unrequiredMaterials = parametersToMaterials(selectedParamList).toSet
            val allDeps = symbolDeps(m.asMethod.returnType)
            val filtered = allDeps.filterNot(d => unrequiredMaterials.exists(m => d.wireWith.symbol =:= m.wireWith.symbol))

            filtered
        }

      case _ =>
        Seq()
    }

  }

  private def parametersToMaterials(selectedParamList: List[TypeSymb]): List[Association] = {
    selectedParamList.map {
      parameter =>
        Association.Parameter(parameter, keyProvider.keyFromParameter(parameter))
    }
  }

  override def isConcrete(symb: TypeFull): Boolean = {
    symb.typeSymbol.isClass && !symb.typeSymbol.isAbstract
  }

  override def isWireableAbstract(symb: TypeFull): Boolean = {
    symb.typeSymbol.isClass && symb.typeSymbol.isAbstract && symb.members.filter(_.isAbstract).forall(m => isWireableMethod(symb, m))
  }

  override def isFactory(symb: TypeFull): Boolean = {
    symb.typeSymbol.isClass && symb.typeSymbol.isAbstract && symb.members.filter(_.isAbstract).forall(m => isFactoryMethod(symb, m) || isWireableMethod(symb, m))
  }

  private def isWireableMethod(tpe: TypeFull, decl: TypeSymb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      decl.info.paramLists.isEmpty && decl.asMethod.returnType != tpe
    }
  }

  private def isFactoryMethod(tpe: TypeFull, decl: TypeSymb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      val paramLists = decl.info.paramLists
      paramLists.nonEmpty && paramLists.forall(list => !list.contains(decl.asMethod.returnType) && !list.contains(tpe))
    }
  }

  private object ConcreteSymbol {
    def unapply(arg: TypeFull): Option[TypeFull] = Some(arg).filter(isConcrete)
  }

  private object AbstractSymbol {
    def unapply(arg: TypeFull): Option[TypeFull] = Some(arg).filter(isWireableAbstract)
  }


  private object FactorySymbol {
    def unapply(arg: TypeFull): Option[(TypeFull, Seq[TypeSymb])] = {
      Some(arg).filter(isFactory).map(f => (f, f.members.filter(m => isFactoryMethod(f, m)).toSeq))
    }
  }

}
