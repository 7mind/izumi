package izumi.distage.reflection

import izumi.distage.model.reflection.SymbolIntrospector
import izumi.distage.model.reflection.universe.DIUniverse
import izumi.fundamentals.reflection.macrortti.LightTypeTag.ReflectionLock

trait SymbolIntrospectorDefaultImpl extends SymbolIntrospector {

  override def selectConstructor(symb: u.SafeType): Option[SelectedConstructor] = ReflectionLock.synchronized {
    selectConstructorMethod(symb).map {
      selectedConstructor =>
        val originalParamListTypes = selectedConstructor.paramLists.map(_.map(_.typeSignature))
        val paramLists = selectedConstructor.typeSignatureIn(symb.tpe).paramLists
        // Hack due to .typeSignatureIn throwing out type annotations...
        val paramsWithAnnos = originalParamListTypes
          .zip(paramLists)
          .map {
            case (origTypes, params) =>
              origTypes.zip(params).map {
                case (o: u.u.AnnotatedTypeApi, p) =>
                  u.SymbolInfo.Runtime(p, symb, o.underlying.typeSymbol.isParameter, o.annotations)
                case (o, p) =>
                  u.SymbolInfo.Runtime(p, symb, o.typeSymbol.isParameter)
              }
          }
        SelectedConstructor(selectedConstructor, paramsWithAnnos)
    }
  }


  override def hasConstructor(tpe: u.SafeType): Boolean = ReflectionLock.synchronized {
    val constructor = findConstructor(tpe)
    constructor.isConstructor
  }

  override def selectConstructorMethod(tpe: u.SafeType): Option[u.MethodSymb] = ReflectionLock.synchronized {
    val constructor = findConstructor(tpe)
    if (!constructor.isTerm) {
      None
    } else {
      Some(constructor.asTerm.alternatives.head.asMethod)
    }
  }

  override def selectNonImplicitParameters(symb: u.MethodSymb): List[List[u.Symb]] = ReflectionLock.synchronized {
    symb.paramLists.takeWhile(_.headOption.forall(!_.isImplicit))
  }

  override def isConcrete(tpe: u.TypeNative): Boolean = {
    tpe match {
      case _: u.u.RefinedTypeApi =>
        // 1. refinements never have a valid constructor unless they are tautological and can be substituted by a class
        // 2. ignoring non-runtime refinements (type members, covariant overrides) leads to unsoundness
        // rt.parents.size == 1 && !rt.decls.exists(_.isAbstract)
        false

      case _ =>
        tpe.typeSymbol.isClass && !tpe.typeSymbol.isAbstract
    }
  }

  override def isWireableAbstract(tpe: u.TypeNative): Boolean = ReflectionLock.synchronized {
    val abstractMembers = tpe.members.filter(_.isAbstract)

    // no mistake here. Wireable astract is a abstract class or class with an abstract parent having all abstract members wireable
    tpe match {
      case rt: u.u.RefinedTypeApi =>
        rt.parents.exists(_.typeSymbol.isAbstract) && abstractMembers.forall(m => isWireableMethod(tpe, m))

      case t =>
        t.typeSymbol.isClass && t.typeSymbol.isAbstract && abstractMembers.forall(m => isWireableMethod(tpe, m))
    }
  }


  override def isFactory(tpe: u.TypeNative): Boolean = ReflectionLock.synchronized {
    tpe.typeSymbol.isClass && tpe.typeSymbol.isAbstract && {
      val abstracts = tpe.members.filter(_.isAbstract)
      abstracts.exists(isFactoryMethod(tpe, _)) &&
        abstracts.forall(m => isFactoryMethod(tpe, m) || isWireableMethod(tpe, m))
    }
  }

  override def isWireableMethod(tpe: u.TypeNative, decl: u.Symb): Boolean = ReflectionLock.synchronized {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      decl.asMethod.paramLists.isEmpty && u.SafeType(decl.asMethod.returnType) != tpe
    }
  }

  override def isFactoryMethod(tpe: u.TypeNative, decl: u.Symb): Boolean = ReflectionLock.synchronized {
    decl.isMethod && decl.isAbstract && !decl.isSynthetic && {
      val paramLists = decl.asMethod.paramLists
      paramLists.nonEmpty && paramLists.forall { list =>
        !list.exists(_.typeSignature =:= decl.asMethod.returnType) && !list.exists(_.typeSignature =:= tpe)
      }
    }
  }

  override def findSymbolAnnotation(annType: u.TypeNative, symb: u.SymbolInfo): Option[u.u.Annotation] = ReflectionLock.synchronized {
    symb.findUniqueAnnotation(annType)
  }

  private def findConstructor(tpe: u.SafeType): u.u.Symbol = {
    tpe.tpe.decl(u.u.termNames.CONSTRUCTOR)
  }
}

object SymbolIntrospectorDefaultImpl {

  class Runtime
    extends SymbolIntrospector.Runtime
      with SymbolIntrospectorDefaultImpl

  object Static {
    def apply(macroUniverse: DIUniverse): SymbolIntrospector.Static[macroUniverse.type] =
      new SymbolIntrospectorDefaultImpl {
        override val u: macroUniverse.type = macroUniverse
      }
  }

}
