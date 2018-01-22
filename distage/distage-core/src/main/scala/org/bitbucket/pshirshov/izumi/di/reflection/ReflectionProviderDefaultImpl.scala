package org.bitbucket.pshirshov.izumi.di.reflection

import org.bitbucket.pshirshov.izumi.di.model.exceptions.UnsupportedWiringException
import org.bitbucket.pshirshov.izumi.di.model.plan.{Association, UnaryWiring, Wiring}
import org.bitbucket.pshirshov.izumi.di.model.{Callable, EqualitySafeType}
import org.bitbucket.pshirshov.izumi.di.{MethodSymb, TypeFull, TypeSymb}

import scala.reflect.runtime.universe

case class SelectedConstructor(constructorSymbol: TypeSymb, arguments: Seq[TypeSymb])

class ReflectionProviderDefaultImpl(keyProvider: DependencyKeyProvider) extends ReflectionProvider {
  override def symbolToWiring(symbl: TypeFull): Wiring = {
    symbl match {
      case FactorySymbol(_, factoryMethods) =>
        val mw = factoryMethods.map(_.asMethod).map {
          factoryMethod =>
            val selectedParamList = selectParameters(factoryMethod)
            val context = DependencyContext.MethodParameterContext(symbl, factoryMethod)
            
            val alreadyInSignature = selectedParamList.map(keyProvider.keyFromParameter(context, _).symbol).toSet
            val resultType = EqualitySafeType(factoryMethod.returnType)
            val methodTypeWireable = unarySymbolDeps(resultType, alreadyInSignature)
            Wiring.FactoryMethod.WithContext(factoryMethod, methodTypeWireable)
        }
        
        Wiring.FactoryMethod(symbl, mw)

      case o =>
        unarySymbolDeps(o, Set.empty)
    }
  }

  override def providerToWiring(function: Callable): Wiring = {
    val associations = function.argTypes.map {
      parameter =>
        Association.Parameter(DependencyContext.CallableParameterContext(function), parameter.tpe.typeSymbol, keyProvider.keyFromType(parameter))
    }
    UnaryWiring.Function(function, associations)
  }

  protected def unarySymbolDeps(symbl: TypeFull, exclusions: Set[TypeFull]): UnaryWiring = {
    symbl match {
      case ConcreteSymbol(symb) =>
        val selected = selectConstructor(symb)
        val context = DependencyContext.ConstructorParameterContext(symbl, selected)
        val materials = selected.arguments.map {
          parameter =>
            Association.Parameter(context, parameter, keyProvider.keyFromParameter(context, parameter))
        }
        val parameters = materials.filterNot(d => exclusions.contains(d.wireWith.symbol))
        UnaryWiring.Constructor(symbl, selected.constructorSymbol, parameters)

      case AbstractSymbol(symb) =>
        // empty paramLists means parameterless method, List(List()) means nullarg method()
        val declaredAbstractMethods = symb.tpe.members.filter(d => isWireableMethod(symb, d)).map(_.asMethod)
        val context = DependencyContext.MethodContext(symbl)
        val materials = declaredAbstractMethods.map {
          method =>
            Association.Method(context, method, keyProvider.keyFromMethod(context, method))
        }
        val methods = materials.filterNot(d => exclusions.contains(d.wireWith.symbol))
        UnaryWiring.Abstract(symbl, methods.toSeq)

      case FactorySymbol(_, _) =>
        throw new UnsupportedWiringException(s"Factory cannot produce factories, it's pointless: $symbl", symbl)

      case _ =>
        throw new UnsupportedWiringException(s"Wiring unsupported: $symbl", symbl)
    }
  }

  protected def selectConstructor(symb: TypeFull): SelectedConstructor = {
    ReflectionProviderDefaultImpl.selectConstructor(symb)
  }
  protected def selectParameters(symb: MethodSymb): List[TypeSymb] = {
    symb.paramLists.head
  }


  protected def isConcrete(symb: TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && !symb.tpe.typeSymbol.isAbstract
  }

  protected def isWireableAbstract(symb: TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && symb.tpe.typeSymbol.isAbstract && symb.tpe.members.filter(_.isAbstract).forall(m => isWireableMethod(symb, m))
  }

  protected def isFactory(symb: TypeFull): Boolean = {
    symb.tpe.typeSymbol.isClass && symb.tpe.typeSymbol.isAbstract && {
      val abstracts = symb.tpe.members.filter(_.isAbstract)
      abstracts.exists(isFactoryMethod(symb, _)) &&
        abstracts.forall(m => isFactoryMethod(symb, m) || isWireableMethod(symb, m))
    }
  }

  protected def isWireableMethod(tpe: TypeFull, decl: TypeSymb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      decl.asMethod.paramLists.isEmpty && EqualitySafeType(decl.asMethod.returnType) != tpe
    }
  }

  protected def isFactoryMethod(tpe: TypeFull, decl: TypeSymb): Boolean = {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      val paramLists = decl.asMethod.paramLists
      paramLists.nonEmpty && paramLists.forall(list => !list.contains(decl.asMethod.returnType) && !list.contains(tpe))
    }
  }

  protected object ConcreteSymbol {
    def unapply(arg: TypeFull): Option[TypeFull] = Some(arg).filter(isConcrete)
  }

  protected object AbstractSymbol {
    def unapply(arg: TypeFull): Option[TypeFull] = Some(arg).filter(isWireableAbstract)
  }

  protected object FactorySymbol {
    def unapply(arg: TypeFull): Option[(TypeFull, Seq[TypeSymb])] =
      Some(arg)
        .filter(isFactory)
        .map(f => (f, f.tpe.members.filter(m => isFactoryMethod(f, m)).toSeq))
  }

}


object ReflectionProviderDefaultImpl {
  // we need this thing here for bootstrap purposes only
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