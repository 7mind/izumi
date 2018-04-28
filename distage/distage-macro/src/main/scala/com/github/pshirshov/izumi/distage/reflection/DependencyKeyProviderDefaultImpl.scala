package com.github.pshirshov.izumi.distage.reflection

import com.github.pshirshov.izumi.distage.model.definition.Id
import com.github.pshirshov.izumi.distage.model.reflection.DependencyKeyProvider
import com.github.pshirshov.izumi.distage.model.reflection.universe.StaticDIUniverse
import com.github.pshirshov.izumi.fundamentals.reflection.AnnotationTools

import scala.reflect.api.Universe

trait DependencyKeyProviderDefaultImpl extends DependencyKeyProvider {
  import u._
  import u.u._

  override def keyFromParameter(context: DependencyContext.ParameterContext, parameterSymbol: Symb): DIKey = {
    val typeKey = DIKey.TypeKey(SafeType(parameterSymbol.typeSignatureIn(context.definingClass.tpe)))

    withOptionalName(parameterSymbol, typeKey)
  }

  override def keyFromMethod(context: DependencyContext.MethodContext, methodSymbol: MethodSymb): DIKey = {
    val typeKey = DIKey.TypeKey(SafeType(methodSymbol.typeSignatureIn(context.definingClass.tpe).finalResultType))
    withOptionalName(methodSymbol, typeKey)
  }

  private def withOptionalName(parameterSymbol: Symb, typeKey: DIKey.TypeKey) = {
    bugAnnotationCall(parameterSymbol)
      .flatMap {
        _.tree.children.tail.collectFirst {
          case Literal(Constant(name: String)) =>
            name
        }
      } match {
      case Some(ann) =>
        typeKey.named(ann)

      case _ =>
        typeKey
    }
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
  protected def bugAnnotationCall(parameterSymbol: Symb): Option[Annotation]

}

object DependencyKeyProviderDefaultImpl {

  class Runtime
    extends DependencyKeyProvider.Runtime
       with DependencyKeyProviderDefaultImpl {
    override protected def bugAnnotationCall(parameterSymbol: u.Symb): Option[u.u.Annotation] =
      AnnotationTools.find[Id](u.u)(parameterSymbol)
  }

  class Static[M <: StaticDIUniverse[_ <: Universe]](macroUniverse: M)
    extends DependencyKeyProvider.Static[M](macroUniverse)
       with DependencyKeyProviderDefaultImpl {
    override protected def bugAnnotationCall(parameterSymbol: u.Symb): Option[u.u.Annotation] =
      AnnotationTools.find[Id](u.u)(parameterSymbol)
  }
  object Static {
    def instance[M <: StaticDIUniverse[_]](macroUniverse: M): Static[macroUniverse.type] =
      new Static(macroUniverse)
  }
}

