package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.model.exceptions.DIException
import org.bitbucket.pshirshov.izumi.di.model.{Callable, EqualitySafeType}
import org.bitbucket.pshirshov.izumi.di.model.plan.{Association, UnaryWireable, Wireable}
import org.bitbucket.pshirshov.izumi.di.reflection.ReflectionProviderDefaultImpl.SelectedConstructor
import org.bitbucket.pshirshov.izumi.di.{MethodSymb, TypeFull, TypeSymb}

import scala.reflect.runtime.universe


class ReflectionProviderDefaultImpl(keyProvider: DependencyKeyProvider) extends ReflectionProvider {

  private def unarySymbolDeps(symbl: TypeFull, exclusions: Set[TypeFull]): UnaryWireable = {
    symbl match {
      case ConcreteSymbol(symb) =>
        val selected = selectConstructor(symb)
        val parameters = parametersToMaterials(selected.arguments).filterNot(d => exclusions.contains(d.wireWith.symbol))
        Wireable.Constructor(symbl, selected.constructorSymbol, parameters)

      case AbstractSymbol(symb) =>
        // empty paramLists means parameterless method, List(List()) means nullarg method()
        val declaredAbstractMethods = symb.tpe.members.filter(d => isWireableMethod(symb, d)).map(_.asMethod)
        val methods = methodsToMaterials(declaredAbstractMethods).filterNot(d => exclusions.contains(d.wireWith.symbol))
        Wireable.Abstract(symbl, methods)

      case FactorySymbol(_, _) =>
        throw new DIException(s"Factory cannot produce factories, it's pointless", null)

      case _ =>
        Wireable.Empty()
    }
  }

  override def symbolDeps(symbl: TypeFull): Wireable = {
    symbl match {
      case FactorySymbol(_, factoryMethods) =>

        val mw = factoryMethods.map {
          m =>
            val paramLists = m.asMethod.paramLists
            val selectedParamList = paramLists.head
            val alreadyInSignature = parametersToMaterials(selectedParamList).map(_.wireWith.symbol).toSet
            val resultType = m.asMethod.returnType

            val methodTypeWireable = unarySymbolDeps(EqualitySafeType(resultType), alreadyInSignature)
            Wireable.Indirect(m, methodTypeWireable)
        }
        Wireable.FactoryMethod(symbl, mw)

      case o =>
        unarySymbolDeps(o, Set.empty)
    }
  }

  override def providerDeps(function: Callable): Wireable = {
    val associations = function.argTypes.map {
      parameter =>
        Association.Parameter(parameter.tpe.typeSymbol, keyProvider.keyFromType(parameter))
    }
    Wireable.Function(function, associations)
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


  def selectConstructor(symb: TypeFull): SelectedConstructor = ReflectionProviderDefaultImpl.selectConstructor(symb)

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

  case class SelectedConstructor(constructorSymbol: TypeSymb, arguments: Seq[TypeSymb])

  def selectConstructor(symb: TypeFull): SelectedConstructor = {
    // val constructors = symb.symbol.members.filter(_.isConstructor)
    // TODO: list should not be empty (?) and should has only one element (?)

    // TODO: only considers scala constructors
    val selectedConstructor = symb.tpe.decl(universe.termNames.CONSTRUCTOR)

    val paramLists = selectedConstructor.asMethod.paramLists
    // TODO: param list should not be empty (?), what to do with multiple lists?..
    SelectedConstructor(selectedConstructor, paramLists.head)

  }
}