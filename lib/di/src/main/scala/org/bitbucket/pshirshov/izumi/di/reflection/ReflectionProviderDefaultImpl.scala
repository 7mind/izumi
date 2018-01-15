package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.model.EqualitySafeType
import org.bitbucket.pshirshov.izumi.di.model.plan.Association
import org.bitbucket.pshirshov.izumi.di.{TypeFull, TypeSymb}

import scala.reflect.runtime.universe


class ReflectionProviderDefaultImpl(keyProvider: DependencyKeyProvider) extends ReflectionProvider {

  override def symbolDeps(symbl: TypeFull): Seq[Association] = {
    symbl match {
      case ConcreteSymbol(symb) =>
        // val constructors = symb.symbol.members.filter(_.isConstructor)
        // TODO: list should not be empty (?) and should has only one element (?)

        // TODO: only considers scala constructors
        val selectedConstructor = symb.symbol.decl(universe.termNames.CONSTRUCTOR)

        val paramLists = selectedConstructor.info.paramLists
        // TODO: param list should not be empty (?), what to do with multiple lists?..
        val selectedParamList = paramLists.head

        parametersToMaterials(selectedParamList)

      case AbstractSymbol(symb) =>
        // empty paramLists means parameterless method, List(List()) means nullarg method()
        val declaredAbstractMethods = symb.symbol.members.filter(d => isWireableMethod(symb, d))
        methodsToMaterials(declaredAbstractMethods)

      case FactorySymbol(_, factoryMethods) =>
        factoryMethods.flatMap {
          m =>
            val paramLists = m.asMethod.info.paramLists
            val selectedParamList = paramLists.head

            val unrequiredMaterials = parametersToMaterials(selectedParamList).map(_.wireWith.symbol).toSet
            val allDeps = symbolDeps(EqualitySafeType(m.asMethod.returnType))
            val filtered = allDeps.filterNot(unrequiredMaterials contains _.wireWith.symbol)

            filtered
        }

      case _ =>
        Seq()
    }

  }

  private def methodsToMaterials(declaredAbstractMethods: Iterable[universe.Symbol]): Seq[Association.Method] = {
    declaredAbstractMethods.map {
      method =>
        Association.Method(method, keyProvider.keyFromMethod(method))
    }.toSeq
  }

  private def parametersToMaterials(selectedParamList: List[TypeSymb]): List[Association.Parameter] = {
    selectedParamList.map {
      parameter =>
        Association.Parameter(parameter, keyProvider.keyFromParameter(parameter))
    }
  }

  override def isConcrete(symb: TypeFull): Boolean = {
    symb.symbol.typeSymbol.isClass && !symb.symbol.typeSymbol.isAbstract
  }

  override def isWireableAbstract(symb: TypeFull): Boolean = {
    symb.symbol.typeSymbol.isClass && symb.symbol.typeSymbol.isAbstract && symb.symbol.members.filter(_.isAbstract).forall(m => isWireableMethod(symb, m))
  }

  override def isFactory(symb: TypeFull): Boolean = {
    symb.symbol.typeSymbol.isClass && symb.symbol.typeSymbol.isAbstract && symb.symbol.members.filter(_.isAbstract).forall(m => isFactoryMethod(symb, m) || isWireableMethod(symb, m))
  }

  private def isWireableMethod(tpe: TypeFull, decl: TypeSymb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      decl.info.paramLists.isEmpty && EqualitySafeType(decl.asMethod.returnType) != tpe
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
    def unapply(arg: TypeFull): Option[(TypeFull, Seq[TypeSymb])] =
      Some(arg).filter(isFactory)
        .map(f => (f, f.symbol.members.filter(m => isFactoryMethod(f, m)).toSeq))
  }

}
