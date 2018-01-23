package org.bitbucket.pshirshov.izumi.distage.reflection

import org.bitbucket.pshirshov.izumi.distage.commons.AnnotationTools
import org.bitbucket.pshirshov.izumi.distage.definition.With
import org.bitbucket.pshirshov.izumi.distage.model.exceptions.UnsupportedWiringException
import org.bitbucket.pshirshov.izumi.distage.model.plan.{Association, UnaryWiring, Wiring}
import org.bitbucket.pshirshov.izumi.distage.model.{Callable, EqualitySafeType}
import org.bitbucket.pshirshov.izumi.distage.{TypeFull, TypeSymb}


class ReflectionProviderDefaultImpl(
                                     keyProvider: DependencyKeyProvider
                                     , symbolIntrospector: SymbolIntrospector
                                   ) extends ReflectionProvider {
  override def symbolToWiring(symbl: TypeFull): Wiring = {
    symbl match {
      case FactorySymbol(_, factoryMethods) =>
        val mw = factoryMethods.map(_.asMethod).map {
          factoryMethod =>
            val resultType = AnnotationTools
              .find[With[_]](factoryMethod)
              .map(_.tree.tpe.typeArgs.head) match {
              case Some(tpe) =>
                EqualitySafeType(tpe)

              case None =>
                EqualitySafeType(factoryMethod.returnType)
            }

            val context = DependencyContext.MethodParameterContext(symbl, factoryMethod)


            val alreadyInSignature = symbolIntrospector
              .selectParameters(factoryMethod)
              .map(keyProvider.keyFromParameter(context, _).symbol)
              .toSet
            
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
        val selected = symbolIntrospector.selectConstructor(symb)
        val context = DependencyContext.ConstructorParameterContext(symbl, selected)
        val materials = selected.arguments.map {
          parameter =>
            Association.Parameter(context, parameter, keyProvider.keyFromParameter(context, parameter))
        }
        val parameters = materials.filterNot(d => exclusions.contains(d.wireWith.symbol))
        UnaryWiring.Constructor(symbl, selected.constructorSymbol, parameters)

      case AbstractSymbol(symb) =>
        // empty paramLists means parameterless method, List(List()) means nullarg method()
        val declaredAbstractMethods = symb.tpe.members.filter(d => symbolIntrospector.isWireableMethod(symb, d)).map(_.asMethod)
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


  protected object ConcreteSymbol {
    def unapply(arg: TypeFull): Option[TypeFull] = Some(arg).filter(symbolIntrospector.isConcrete)
  }

  protected object AbstractSymbol {
    def unapply(arg: TypeFull): Option[TypeFull] = Some(arg).filter(symbolIntrospector.isWireableAbstract)
  }

  protected object FactorySymbol {
    def unapply(arg: TypeFull): Option[(TypeFull, Seq[TypeSymb])] =
      Some(arg)
        .filter(symbolIntrospector.isFactory)
        .map(f => (f, f.tpe.members.filter(m => symbolIntrospector.isFactoryMethod(f, m)).toSeq))
  }

}
