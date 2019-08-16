package izumi.distage.reflection

import izumi.distage.model.definition.{Id, With}
import izumi.distage.model.exceptions.BadIdAnnotationException
import izumi.distage.model.reflection.universe.DIUniverse
import izumi.distage.model.reflection.{DependencyKeyProvider, SymbolIntrospector}

trait DependencyKeyProviderDefaultImpl extends DependencyKeyProvider {
  import u._

  protected def symbolIntrospector: SymbolIntrospector.Aux[u.type]

  override def keyFromParameter(context: DependencyContext.ParameterContext, parameterSymbol: SymbolInfo): DIKey.BasicKey = {
    val typeKey = if (parameterSymbol.isByName) {
      DIKey.TypeKey(SafeType(parameterSymbol.finalResultType.use(_.typeArgs.head.finalResultType)))
    } else {
      DIKey.TypeKey(parameterSymbol.finalResultType)
    }

    withOptionalName(parameterSymbol, typeKey)
  }

  override def associationFromParameter(parameterSymbol: u.SymbolInfo): u.Association.Parameter = {
    val context = DependencyContext.ConstructorParameterContext(parameterSymbol.definingClass, parameterSymbol)

    Association.Parameter(
      context
      , parameterSymbol.name
      , parameterSymbol.finalResultType
      , keyFromParameter(context, parameterSymbol)
      , parameterSymbol.isByName
      , parameterSymbol.wasGeneric
    )
  }

  override def keyFromMethod(context: DependencyContext.MethodContext, methodSymbol: SymbolInfo): DIKey.BasicKey = {
    val typeKey = DIKey.TypeKey(methodSymbol.finalResultType)
    withOptionalName(methodSymbol, typeKey)
  }

  override def resultOfFactoryMethod(context: u.DependencyContext.MethodParameterContext): u.SafeType = {
    context.factoryMethod.findUniqueAnnotation(typeOfWithAnnotation) match {
      case Some(With(tpe)) =>
        tpe
      case _ =>
        context.factoryMethod.finalResultType
    }
  }

  private def withOptionalName(parameterSymbol: SymbolInfo, typeKey: DIKey.TypeKey): u.DIKey.BasicKey =
    symbolIntrospector.findSymbolAnnotation(typeOfIdAnnotation, parameterSymbol) match {
      case Some(Id(name)) =>
        typeKey.named(name)
      case Some(v) =>
        throw new BadIdAnnotationException(typeOfIdAnnotation.toString, v)
      case _ =>
        typeKey
    }

  protected def typeOfWithAnnotation: u.SafeType
  protected def typeOfIdAnnotation: u.SafeType

}

object DependencyKeyProviderDefaultImpl {

  class Runtime
  (
    override val symbolIntrospector: SymbolIntrospector.Runtime
  ) extends DependencyKeyProvider.Runtime
       with DependencyKeyProviderDefaultImpl {
    // workaround for a scalac bug that fails at runtime if `typeOf` is called or a `TypeTag` is summoned in source code
    // when the universe is abstract (a parameter, not a singleton runtime.universe: JavaUniverse), generates runtime exceptions like
    //
    // [info]   scala.ScalaReflectionException: class izumi.distage.model.definition.Id in JavaMirror with java.net.URLClassLoader@3911c2a7 of type class java.net.URLClassLoader with classpath [file:/Users/mykhailo.feldman/.sbt/boot/scala-2.12.4/lib/scala-library.jar,file:/Users/mykhailo.feldman/.sbt/boot/scala-2.12.4/lib/scala-compiler.jar,file:/Users/mykhailo.feldman/.sbt/boot/scala-2.12.4/lib/jline.jar,file:/Users/mykhailo.feldman/.sbt/boot/scala-2.12.4/lib/scala-reflect.jar,file:/Users/mykhailo.feldman/.sbt/boot/scala-2.12.4/lib/scala-xml_2.12.jar] and parent being xsbt.boot.BootFilteredLoader@200a26bc of type class xsbt.boot.BootFilteredLoader with classpath [<unknown>] and parent being jdk.internal.loader.ClassLoaders$AppClassLoader@4f8e5cde of type class jdk.internal.loader.ClassLoaders$AppClassLoader with classpath [<unknown>] and parent being jdk.internal.loader.ClassLoaders$PlatformClassLoader@1bc6a36e of type class jdk.internal.loader.ClassLoaders$PlatformClassLoader with classpath [<unknown>] and parent being primordial classloader with boot classpath [<unknown>] not found.
    // [info]   at scala.reflect.internal.Mirrors$RootsBase.staticClass(Mirrors.scala:122)
    // [info]   at scala.reflect.internal.Mirrors$RootsBase.staticClass(Mirrors.scala:22)
    // [info]   at izumi.distage.reflection.DependencyKeyProviderDefaultImpl$$typecreator1$1.apply(DependencyKeyProviderDefaultImpl.scala:22)
    // [info]   at scala.reflect.api.TypeTags$WeakTypeTagImpl.tpe$lzycompute(TypeTags.scala:230)
    // [info]   at scala.reflect.api.TypeTags$WeakTypeTagImpl.tpe(TypeTags.scala:230)
    // [info]   at izumi.fundamentals.reflection.AnnotationTools$.$anonfun$find$1(AnnotationTools.scala:9)
    // [info]   at izumi.fundamentals.reflection.AnnotationTools$.$anonfun$find$1$adapted(AnnotationTools.scala:8)
    // [info]   at scala.collection.LinearSeqOptimized.find(LinearSeqOptimized.scala:111)
    // [info]   at scala.collection.LinearSeqOptimized.find$(LinearSeqOptimized.scala:108)
    // [info]   at scala.collection.immutable.List.find(List.scala:86)
    // [info]   ...
    //
    // So these calls are delayed to a point at which the universe is a known concrete type
    override protected val typeOfWithAnnotation: u.SafeType = u.SafeType(u.u.typeOf[Any])
    override protected val typeOfIdAnnotation: u.SafeType = u.SafeType(u.u.typeOf[Id])
  }

  object Static {
    def apply(macroUniverse: DIUniverse)(symbolintrospector: SymbolIntrospector.Static[macroUniverse.type]): DependencyKeyProvider.Static[macroUniverse.type] = {
      new DependencyKeyProviderDefaultImpl {
        override final val u: macroUniverse.type = macroUniverse
        override final val symbolIntrospector: symbolintrospector.type = symbolintrospector

        override protected val typeOfWithAnnotation: u.SafeType = u.SafeType(u.u.typeOf[With[Any]])
        override protected val typeOfIdAnnotation: u.SafeType = u.SafeType(u.u.typeOf[Id])
      }
    }
  }

}

