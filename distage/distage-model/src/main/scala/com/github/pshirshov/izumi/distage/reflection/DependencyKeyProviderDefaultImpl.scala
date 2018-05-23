package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.definition.{Id, With}
import com.github.pshirshov.izumi.distage.model.reflection.universe.DIUniverse
import com.github.pshirshov.izumi.distage.model.reflection.{DependencyKeyProvider, SymbolIntrospector}

trait DependencyKeyProviderDefaultImpl extends DependencyKeyProvider {
  import u._

  protected def symbolIntrospector: SymbolIntrospector.Aux[u.type]

  override def keyFromParameter(context: DependencyContext.ParameterContext, parameterSymbol: SymbolInfo): DIKey = {
    val typeKey = DIKey.TypeKey(parameterSymbol.finalResultType)
    withOptionalName(parameterSymbol, typeKey)
  }

  override def keyFromParameterType(parameterType: u.TypeFull): u.DIKey = {
    val typeKey = DIKey.TypeKey(parameterType)
    symbolIntrospector.findTypeAnnotation(typeOfIdAnnotation, parameterType) match {
      case Some(Id(name)) =>
        typeKey.named(name)
      case _ =>
        typeKey
    }
  }

  override def keyFromMethod(context: DependencyContext.MethodContext, methodSymbol: SymbolInfo): DIKey = {
    val typeKey = DIKey.TypeKey(methodSymbol.finalResultType)
    withOptionalName(methodSymbol, typeKey)
  }

  override def resultOfFactoryMethod(context: u.DependencyContext.MethodParameterContext): u.SafeType =
    context.factoryMethod.findAnnotation(typeOfWithAnnotation) match {
      case Some(With(tpe)) =>
        tpe
      case _ =>
        context.factoryMethod.finalResultType
    }

  private def withOptionalName(parameterSymbol: SymbolInfo, typeKey: DIKey.TypeKey) =
    symbolIntrospector.findSymbolAnnotation(typeOfIdAnnotation, parameterSymbol) match {
      case Some(Id(name)) =>
        typeKey.named(name)
      case _ =>
        typeKey
    }

  protected def typeOfWithAnnotation: u.SafeType
  protected def typeOfIdAnnotation: u.SafeType

}

object DependencyKeyProviderDefaultImpl {

  class Runtime(
                 override val symbolIntrospector: SymbolIntrospector.Runtime
               )
    extends DependencyKeyProvider.Runtime
       with DependencyKeyProviderDefaultImpl {
    // workaround for a scalac bug that fails at runtime if `typeOf` is called or a `TypeTag` is summoned
    // when the universe is abstract (a parameter, not singleton runtime.universe JavaUniverse), generates runtime exceptions like
    //
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
    // So these calls are moved to a point in which the universe is instantiated
    override protected val typeOfWithAnnotation: u.SafeType = u.SafeType.get[With[Any]]
    override protected val typeOfIdAnnotation: u.SafeType = u.SafeType.get[Id]
  }

  object Static {
    def apply(macroUniverse: DIUniverse)(symbolintrospector: SymbolIntrospector.Static[macroUniverse.type]): DependencyKeyProvider.Static[macroUniverse.type] = {
      new DependencyKeyProviderDefaultImpl {
        override val u: macroUniverse.type = macroUniverse
        override val symbolIntrospector: symbolintrospector.type = symbolintrospector

        override protected val typeOfWithAnnotation: u.SafeType = u.SafeType.get[With[Any]]
        override protected val typeOfIdAnnotation: u.SafeType = u.SafeType.get[Id]
      }
    }
  }

}

