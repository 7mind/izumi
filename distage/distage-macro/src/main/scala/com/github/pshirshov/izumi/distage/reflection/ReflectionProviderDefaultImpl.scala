package com.github.pshirshov.izumi.distage.reflection
import com.github.pshirshov.izumi.distage.model.definition.With
import com.github.pshirshov.izumi.distage.model.exceptions.{DIException, UnsupportedWiringException}
import com.github.pshirshov.izumi.distage.model.reflection.universe.MacroUniverse
import com.github.pshirshov.izumi.distage.model.reflection.{DependencyKeyProvider, ReflectionProvider, SymbolIntrospector}
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

import scala.reflect.api.Universe

trait ReflectionProviderDefaultImpl extends ReflectionProvider {
  self =>

  import u.Wiring._
  import u._

  protected def keyProvider: DependencyKeyProvider { val u: self.u.type }
  protected def symbolIntrospector: SymbolIntrospector { val u: self.u.type }

  def symbolToWiring(symbl: TypeFull): Wiring = {
    symbl match {
      case FactorySymbol(_, factoryMethods, dependencyMethods) =>
        val mw = factoryMethods.map(_.asMethod).map {
          factoryMethod =>
            val resultType = bugAnnotationCall(factoryMethod)
              .map(_.tree.tpe.typeArgs.head) match {
              case Some(tpe) =>
                SafeType(tpe)

              case None =>
                SafeType(factoryMethod.returnType)
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

  // extension point in case we ever want to use tagged types, singleton literals or marker traits for named arguments
  override def providerToWiring(function: u.Callable): u.Wiring = {
    Wiring.UnaryWiring.Function(function)
  }

  override def constructorParameters(symbl: TypeFull): List[Association.Parameter] = {
    val args: List[u.Symb] = symbolIntrospector.selectConstructor(symbl).arguments

    val context = DependencyContext.ConstructorParameterContext(symbl)
    args.map {
      parameter =>
        Association.Parameter(
          context
          , parameter.name.toTermName.toString
          , SafeType(parameter.typeSignatureIn(symbl.tpe))
          , keyProvider.keyFromParameter(context, parameter)
        )
    }
  }

  private def unarySymbolDeps(symbl: TypeFull): UnaryWiring.ProductWiring = symbl match {
    case ConcreteSymbol(symb) =>
      UnaryWiring.Constructor(symb, constructorParameters(symb))

    case AbstractSymbol(symb) =>
      UnaryWiring.Abstract(symb, traitMethods(symb))

    case FactorySymbol(_, _, _) =>
      throw new UnsupportedWiringException(s"Factory cannot produce factories, it's pointless: $symbl", symbl)

    case _ =>
      throw new UnsupportedWiringException(s"Wiring unsupported: $symbl", symbl)
  }

  private def traitMethods(symb: TypeFull): Seq[Association.Method] = {
    // empty paramLists means parameterless method, List(List()) means nullarg unit method()
    val declaredAbstractMethods = symb.tpe.members
      .filter(symbolIntrospector.isWireableMethod(symb, _))
      .map(_.asMethod)
    val context = DependencyContext.MethodContext(symb)
    declaredAbstractMethods.map {
      method =>
        Association.Method(context, method, keyProvider.keyFromMethod(context, method))
    }.toSeq
  }

  protected object ConcreteSymbol {
    def unapply(arg: TypeFull): Option[TypeFull] = Some(arg).filter(symbolIntrospector.isConcrete)
  }

  protected object AbstractSymbol {
    def unapply(arg: TypeFull): Option[TypeFull] = Some(arg).filter(symbolIntrospector.isWireableAbstract)
  }

  protected object FactorySymbol {
    def unapply(arg: TypeFull): Option[(TypeFull, Seq[Symb], Seq[MethodSymb])] =
      Some(arg)
        .filter(symbolIntrospector.isFactory)
        .map(f => (
          f
          , f.tpe.members.filter(m => symbolIntrospector.isFactoryMethod(f, m)).toSeq
          , f.tpe.members.filter(m => symbolIntrospector.isWireableMethod(f, m)).map(_.asMethod).toSeq
        ))
  }

  // workaround scalac bug generating runtime exceptions like
  // [info]   scala.ScalaReflectionException: class com.github.pshirshov.izumi.distage.model.definition.Id in JavaMirror with java.net.URLClassLoader@3911c2a7 of type class java.net.URLClassLoader with classpath [file:/Users/mykhailo.feldman/.sbt/boot/scala-2.12.4/lib/scala-library.jar,file:/Users/mykhailo.feldman/.sbt/boot/scala-2.12.4/lib/scala-compiler.jar,file:/Users/mykhailo.feldman/.sbt/boot/scala-2.12.4/lib/jline.jar,file:/Users/mykhailo.feldman/.sbt/boot/scala-2.12.4/lib/scala-reflect.jar,file:/Users/mykhailo.feldman/.sbt/boot/scala-2.12.4/lib/scala-xml_2.12.jar] and parent being xsbt.boot.BootFilteredLoader@200a26bc of type class xsbt.boot.BootFilteredLoader with classpath [<unknown>] and parent being jdk.internal.loader.ClassLoaders$AppClassLoader@4f8e5cde of type class jdk.internal.loader.ClassLoaders$AppClassLoader with classpath [<unknown>] and parent being jdk.internal.loader.ClassLoaders$PlatformClassLoader@1bc6a36e of type class jdk.internal.loader.ClassLoaders$PlatformClassLoader with classpath [<unknown>] and parent being primordial classloader with boot classpath [<unknown>] not found.
  // [info]   at scala.reflect.internal.Mirrors$RootsBase.staticClass(Mirrors.scala:122)
  // [info]   at scala.reflect.internal.Mirrors$RootsBase.staticClass(Mirrors.scala:22)
  // [info]   at com.github.pshirshov.izumi.distage.reflection.DependencyKeyProviderDefaultImpl$$typecreator1$1.apply(DependencyKeyProviderDefaultImpl.scala:22)
  // [info]   at scala.reflect.api.TypeTags$WeakTypeTagImpl.tpe$lzycompute(TypeTags.scala:230)
  // [info]   at scala.reflect.api.TypeTags$WeakTypeTagImpl.tpe(TypeTags.scala:230)
  // [info]   at com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools$.$anonfun$find$1(AnnotationTools.scala:9)
  // [info]   at com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools$.$anonfun$find$1$adapted(AnnotationTools.scala:8)
  // [info]   at scala.collection.LinearSeqOptimized.find(LinearSeqOptimized.scala:111)
  // [info]   at scala.collection.LinearSeqOptimized.find$(LinearSeqOptimized.scala:108)
  // [info]   at scala.collection.immutable.List.find(List.scala:86)
  // [info]   ...
  //
  // When a method calling .annotations is _declared_ when Universe is abstract (not <: JavaUniverse)
  protected def bugAnnotationCall(parameterSymbol: Symb): Option[u.u.Annotation]

}

object ReflectionProviderDefaultImpl {

  class Java(
              override val keyProvider: DependencyKeyProvider.Java
              , override val symbolIntrospector: SymbolIntrospector.Java
            )
    extends ReflectionProvider.Java
      with ReflectionProviderDefaultImpl {
    override protected def bugAnnotationCall(parameterSymbol: u.Symb): Option[u.u.Annotation] =
      AnnotationTools.find[With[_]](u.u)(parameterSymbol)
  }
  object Java {
    final val instance = new ReflectionProviderDefaultImpl.Java(DependencyKeyProviderDefaultImpl.Java.instance, SymbolIntrospectorDefaultImpl.Java.instance)
  }

  trait Macro[M <: MacroUniverse[_ <: Universe]]
    extends ReflectionProvider.Macro[M]
      with ReflectionProviderDefaultImpl {
    override protected def bugAnnotationCall(parameterSymbol: u.Symb): Option[u.u.Annotation] =
      AnnotationTools.find[With[_]](u.u)(parameterSymbol)
  }
  object Macro {
    // workaround for no path-dependent type support in class constructor
    // https://stackoverflow.com/questions/18077315/dependent-types-not-working-for-constructors#18078333
    def instance[M <: MacroUniverse[_]]
      (macroUniverse: M)
        (keyProvider: DependencyKeyProvider.Macro[macroUniverse.type] ,symbolIntrospector: SymbolIntrospector.Macro[macroUniverse.type])
        : Macro[macroUniverse.type] = {
      class Instance (
                       override val u: macroUniverse.type = macroUniverse
                      , override val keyProvider: DependencyKeyProvider.Macro[macroUniverse.type] = keyProvider
                      , override val symbolIntrospector: SymbolIntrospector.Macro[macroUniverse.type] = symbolIntrospector
                     ) extends Macro[macroUniverse.type]
      new Instance
    }
  }
}
