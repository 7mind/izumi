package com.github.pshirshov.izumi.fundamentals.reflection

import java.lang.reflect.Method

import scala.collection.immutable.ListMap
import scala.reflect.api
import scala.reflect.api.{Mirror, TypeCreator, Universe}
import scala.reflect.internal.Symbols
import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.{universe => u}
import scala.language.reflectiveCalls

object ReflectionUtil {
  def toJavaMethod(definingClass: u.Type, methodSymbol: u.Symbol): Method = {
    // https://stackoverflow.com/questions/16787163/get-a-java-lang-reflect-method-from-a-reflect-runtime-universe-methodsymbol
    val method = methodSymbol.asMethod
    val runtimeClass = currentMirror.runtimeClass(definingClass)
    val mirror = u.runtimeMirror(runtimeClass.getClassLoader)
    val privateMirror = mirror.asInstanceOf[ {
      def methodToJava(sym: Symbols#MethodSymbol): Method
    }]
    val javaMethod = privateMirror.methodToJava(method.asInstanceOf[Symbols#MethodSymbol])
    javaMethod
  }

  def typeToTypeTag[T](u: Universe)(
                        tpe: u.Type,
                        mirror: Mirror[u.type]
                      ): u.TypeTag[T] = {
    val creator: TypeCreator = new reflect.api.TypeCreator {
      def apply[U <: SingletonUniverse](m: Mirror[U]): U#Type = {
        assert(m eq mirror, s"TypeTag[$tpe] defined in $mirror cannot be migrated to $m.")
        tpe.asInstanceOf[U#Type]
      }
    }

    u.TypeTag(mirror, creator)
  }

  implicit final class WeakTypeTagMigrate[T](private val weakTypeTag: Universe#WeakTypeTag[T]) extends AnyVal {
    def migrate[V <: SingletonUniverse](m: api.Mirror[V]): m.universe.WeakTypeTag[T] =
      weakTypeTag.in(m).asInstanceOf[m.universe.WeakTypeTag[T]]
  }

  def deannotate[U <: Universe](typ: U#Type): U#Type =
    typ match {
      case t: U#AnnotatedTypeApi =>
        t.underlying
      case _ =>
        typ
    }

  def kindOf(tpe: Universe#Type): Kind =
    Kind(tpe.typeParams.map(t => kindOf(t.typeSignature)))

  final case class Kind(args: List[Kind]) {
    override def toString: String = format("_")

    def format(typeName: String) = s"$typeName${if (args.nonEmpty) args.mkString("[", ", ", "]") else ""}"
  }

  /**
    * This function is here to just just hide a warning coming from Annotation.apply when macro is expanded.
    * Since c.reifyTree seems to have a bug whereby it injects empty TypeTrees when trying to reify an
    * annotation recovered from a symbol via the .annotations method, it doesn't seem possible to avoid
    * calling this method.
    */
  def runtimeAnnotation(tpe: u.Type, scalaArgs: List[u.Tree], javaArgs: ListMap[u.Name, u.JavaArgument]): u.Annotation =
    u.Annotation.apply(tpe, scalaArgs, javaArgs)



  def toTypeRef(tpe: u.TypeApi): Option[u.TypeRefApi] = {
    tpe match {
      case typeref: u.TypeRefApi =>
        Some(typeref)
      case _ =>
        None
    }
  }
}

