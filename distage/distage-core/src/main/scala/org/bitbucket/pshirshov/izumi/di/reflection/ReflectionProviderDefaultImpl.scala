package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.model.{Callable, EqualitySafeType}
import org.bitbucket.pshirshov.izumi.di.model.plan.Association
import org.bitbucket.pshirshov.izumi.di.{MethodSymb, TypeFull, TypeSymb}

import scala.reflect.runtime.universe


class ReflectionProviderDefaultImpl(keyProvider: DependencyKeyProvider) extends ReflectionProvider {

  override def symbolDeps(symbl: TypeFull): Seq[Association] = {
    symbl match {
      case ConcreteSymbol(symb) =>
        val selectedParamList = ReflectionProviderDefaultImpl.selectConstructor(symb)
        parametersToMaterials(selectedParamList)

      case AbstractSymbol(symb) =>
        // empty paramLists means parameterless method, List(List()) means nullarg method()
        val declaredAbstractMethods = symb.tpe.members.filter(d => isWireableMethod(symb, d)).map(_.asMethod)
        methodsToMaterials(declaredAbstractMethods)

      case FactorySymbol(_, factoryMethods) =>
        factoryMethods.flatMap {
          m =>
            val paramLists = m.asMethod.paramLists
            val selectedParamList = paramLists.head

            val unrequiredMaterials = parametersToMaterials(selectedParamList).map(_.wireWith.symbol).toSet
            val allDeps = symbolDeps(EqualitySafeType(m.asMethod.returnType))
            val filtered = allDeps.filterNot(d => unrequiredMaterials.contains(d.wireWith.symbol))

            filtered
        }

      case _ =>
        Seq()
    }
  }

  override def providerDeps(function: Callable): Seq[Association] = {
    function.argTypes.map {
      parameter =>
        Association.Parameter(parameter.tpe.typeSymbol, keyProvider.keyFromType(parameter))
    }
  }

  private def methodsToMaterials(declaredAbstractMethods: Iterable[MethodSymb]): Seq[Association.Method] = {
    declaredAbstractMethods.map {
      method =>
        Association.Method(method, keyProvider.keyFromMethod(method))
    }.toSeq
  }

  private def parametersToMaterials(selectedParamList: Seq[TypeSymb]): Seq[Association.Parameter] = {
    selectedParamList.map {
      parameter =>
        Association.Parameter(parameter, keyProvider.keyFromParameter(parameter))
    }
  }

  override def isConcrete(symb: TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && !symb.tpe.typeSymbol.isAbstract
  }

  override def isWireableAbstract(symb: TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && symb.tpe.typeSymbol.isAbstract && symb.tpe.members.filter(_.isAbstract).forall(m => isWireableMethod(symb, m))
  }

  override def isFactory(symb: TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && symb.tpe.typeSymbol.isAbstract && symb.tpe.members.filter(_.isAbstract).forall(m => isFactoryMethod(symb, m) || isWireableMethod(symb, m))
  }

  private def isWireableMethod(tpe: TypeFull, decl: TypeSymb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      decl.asMethod.paramLists.isEmpty && EqualitySafeType(decl.asMethod.returnType) != tpe
    }
  }

  private def isFactoryMethod(tpe: TypeFull, decl: TypeSymb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      val paramLists = decl.asMethod.paramLists
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
        .map(f => (f, f.tpe.members.filter(m => isFactoryMethod(f, m)).toSeq))
  }

}

object ReflectionProviderDefaultImpl {
  def selectConstructor(symb: TypeFull): List[TypeSymb] = {
    // val constructors = symb.symbol.members.filter(_.isConstructor)
    // TODO: list should not be empty (?) and should has only one element (?)

    // TODO: only considers scala constructors
    val selectedConstructor = symb.tpe.decl(universe.termNames.CONSTRUCTOR)

    val paramLists = selectedConstructor.asMethod.paramLists
    // TODO: param list should not be empty (?), what to do with multiple lists?..
    paramLists.head

  }

}