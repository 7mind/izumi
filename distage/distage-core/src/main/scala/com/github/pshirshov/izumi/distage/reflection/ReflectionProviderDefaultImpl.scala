package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.commons.AnnotationTools
import com.github.pshirshov.izumi.distage.definition.With
import com.github.pshirshov.izumi.distage.model.exceptions.{DIException, UnsupportedWiringException}
import com.github.pshirshov.izumi.distage.model.plan.{Association, UnaryWiring, Wiring}
import com.github.pshirshov.izumi.distage.model.{Callable, EqualitySafeType}
import com.github.pshirshov.izumi.distage.{MethodSymb, TypeFull, TypeSymb}


class ReflectionProviderDefaultImpl(
                                     keyProvider: DependencyKeyProvider
                                     , symbolIntrospector: SymbolIntrospector
                                   ) extends ReflectionProvider {
  override def symbolToWiring(symbl: TypeFull): Wiring = {
    symbl match {
      case FactorySymbol(_, factoryMethods, dependencyMethods) =>
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
              .map(keyProvider.keyFromParameter(context, _))

            //val symbolsAlreadyInSignature = alreadyInSignature.map(_.symbol).toSet

            val methodTypeWireable = unarySymbolDeps(resultType)

            val excessiveSymbols = alreadyInSignature.toSet -- methodTypeWireable.associations.map(_.wireWith).toSet

            if (excessiveSymbols.nonEmpty) {
              throw new DIException(s"Factory method signature contains symbols which are not required for target product: $excessiveSymbols", null)
            }


            Wiring.FactoryMethod.WithContext(factoryMethod, methodTypeWireable, alreadyInSignature)
        }

        val context = DependencyContext.MethodContext(symbl)
        val materials = dependencyMethods.map {
          method =>
            Association.Method(context, method, keyProvider.keyFromMethod(context, method))
        }

        Wiring.FactoryMethod(symbl, mw, materials)

      case o =>
        unarySymbolDeps(o)
    }
  }

  override def providerToWiring(function: Callable): Wiring = {
    val associations = function.argTypes.map {
      parameter =>
        Association.Parameter(DependencyContext.CallableParameterContext(function), parameter.tpe.typeSymbol, keyProvider.keyFromType(parameter))
    }
    UnaryWiring.Function(function, associations)
  }

  protected def unarySymbolDeps(symbl: TypeFull): UnaryWiring = {
    symbl match {
      case ConcreteSymbol(symb) =>
        val selected = symbolIntrospector.selectConstructor(symb)
        val context = DependencyContext.ConstructorParameterContext(symbl, selected)
        val materials = selected.arguments.map {
          parameter =>
            Association.Parameter(context, parameter, keyProvider.keyFromParameter(context, parameter))
        }
        UnaryWiring.Constructor(symbl, selected.constructorSymbol, materials)

      case AbstractSymbol(symb) =>
        // empty paramLists means parameterless method, List(List()) means nullarg method()
        val declaredAbstractMethods = symb.tpe.members.filter(d => symbolIntrospector.isWireableMethod(symb, d)).map(_.asMethod)
        val context = DependencyContext.MethodContext(symbl)
        val materials = declaredAbstractMethods.map {
          method =>
            Association.Method(context, method, keyProvider.keyFromMethod(context, method))
        }
        UnaryWiring.Abstract(symbl, materials.toSeq)

      case FactorySymbol(_, _, _) =>
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
    def unapply(arg: TypeFull): Option[(TypeFull, Seq[TypeSymb], Seq[MethodSymb])] =
      Some(arg)
        .filter(symbolIntrospector.isFactory)
        .map(f => (
          f
          , f.tpe.members.filter(m => symbolIntrospector.isFactoryMethod(f, m)).toSeq
          , f.tpe.members.filter(m => symbolIntrospector.isWireableMethod(f, m)).map(_.asMethod).toSeq
        ))
  }

}
