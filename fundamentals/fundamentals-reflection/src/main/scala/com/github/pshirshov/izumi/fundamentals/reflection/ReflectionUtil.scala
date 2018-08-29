package com.github.pshirshov.izumi.fundamentals.reflection

import java.lang.reflect.Method

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

  def typeToTypeTag[T](
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
}

