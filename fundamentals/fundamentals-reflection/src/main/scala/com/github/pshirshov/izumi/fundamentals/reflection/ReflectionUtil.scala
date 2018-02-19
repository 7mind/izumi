package com.github.pshirshov.izumi.fundamentals.reflection

import java.lang.reflect.Method

import scala.language.reflectiveCalls
import scala.reflect.api.Mirror
import scala.reflect.internal.Symbols

object ReflectionUtil {
  def toJavaMethod(definingClass: RuntimeUniverse.TypeFull, methodSymbol: RuntimeUniverse.TypeSymb): Method = {
    // https://stackoverflow.com/questions/16787163/get-a-java-lang-reflect-method-from-a-reflect-runtime-universe-methodsymbol
    val method = methodSymbol.asMethod
    val runtimeClass = RuntimeUniverse.mirror.runtimeClass(definingClass.tpe)
    val mirror = RuntimeUniverse.u.runtimeMirror(runtimeClass.getClassLoader)
    val privateMirror = mirror.asInstanceOf[ {
      def methodToJava(sym: Symbols#MethodSymbol): Method
    }]
    val javaMethod = privateMirror.methodToJava(method.asInstanceOf[Symbols#MethodSymbol])
    javaMethod
  }


  def typeToTypeTag[T](
                        tpe: RuntimeUniverse.TypeNative,
                        mirror: reflect.api.Mirror[RuntimeUniverse.u.type]
                      ): RuntimeUniverse.Tag[T] = {
    val creator = new reflect.api.TypeCreator {
      def apply[U <: RuntimeUniverse.SingletonUniverse](m: Mirror[U]): U#Type = {
        assert(m eq mirror, s"TypeTag[$tpe] defined in $mirror cannot be migrated to $m.")
        tpe.asInstanceOf[U#Type]
      }
    }

    RuntimeUniverse.u.TypeTag(mirror, creator)
  }


  import scala.reflect.runtime.universe._

  private lazy val universeMirror = runtimeMirror(getClass.getClassLoader)

  def companionOf[T](implicit tt: TypeTag[T]): Any = {
    val companionMirror = universeMirror.reflectModule(typeOf[T].typeSymbol.companion.asModule)
    companionMirror.instance
  }
}

