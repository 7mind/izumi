package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.Symb
import org.bitbucket.pshirshov.izumi.di.model.plan.Association

import scala.reflect.runtime.universe


class ReflectionProviderDefaultImpl(keyProvider: DependencyKeyProvider) extends ReflectionProvider {

  override def symbolDeps(symbl: Symb): Seq[Association] = {
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
        declaredAbstractMethods.map(m => Association.Method(m, keyProvider.keyFromMethod(m))).toSeq

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

  override def isConcrete(symb: Symb): Boolean = {
    symb.isClass && !symb.isAbstract
  }

  override def isWireableAbstract(symb: Symb): Boolean = {
    symb.isClass && symb.isAbstract && symb.info.decls.filter(_.isAbstract).forall(m => isWireableMethod(symb, m))
  }

  override def isFactory(symb: Symb): Boolean = {
    symb.isClass && symb.isAbstract && symb.info.decls.filter(_.isAbstract).forall(m => isFactoryMethod(symb, m) || isWireableMethod(symb, m))
  }

  private def isWireableMethod(tpe: Symb, decl: Symb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      decl.info.paramLists.isEmpty && decl.asMethod.returnType.typeSymbol != tpe
    }
  }

  private def parametersToMaterials(selectedParamList: List[universe.Symbol]): List[Association] = {
    selectedParamList.map {
      parameter =>
        Association.Parameter(parameter, keyProvider.keyFromParameter(parameter))
    }
  }

  private def isFactoryMethod(tpe: Symb, decl: Symb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      val paramLists = decl.info.paramLists
      paramLists.nonEmpty && paramLists.forall(list => !list.contains(decl.asMethod.returnType) && !list.contains(tpe))
    }
  }

  private object ConcreteSymbol {
    def unapply(arg: Symb): Option[Symb] = Some(arg).filter(isConcrete)
  }

  private object AbstractSymbol {
    def unapply(arg: Symb): Option[Symb] = Some(arg).filter(isWireableAbstract)
  }


  private object FactorySymbol {
    def unapply(arg: Symb): Option[(Symb, Seq[Symb])] = {
      Some(arg).filter(isFactory).map(f => (f, f.info.decls.filter(m => isFactoryMethod(f, m)).toSeq))
    }
  }

}
