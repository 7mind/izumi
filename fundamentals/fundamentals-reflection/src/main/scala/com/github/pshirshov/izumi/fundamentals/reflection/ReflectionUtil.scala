package com.github.pshirshov.izumi.fundamentals.reflection

import java.lang.reflect.Method


import scala.language.reflectiveCalls
import scala.reflect.internal.Symbols
import scala.reflect.runtime.{currentMirror, universe}

object ReflectionUtil {
  def toJavaMethod(definingClass: TypeFull, methodSymbol: TypeSymb): Method = {
    // https://stackoverflow.com/questions/16787163/get-a-java-lang-reflect-method-from-a-reflect-runtime-universe-methodsymbol
    val method = methodSymbol.asMethod
    val runtimeClass = currentMirror.runtimeClass(definingClass.tpe)
    val mirror = universe.runtimeMirror(runtimeClass.getClassLoader)
    val privateMirror = mirror.asInstanceOf[ {
      def methodToJava(sym: Symbols#MethodSymbol): Method
    }]
    val javaMethod = privateMirror.methodToJava(method.asInstanceOf[Symbols#MethodSymbol])
    javaMethod
  }
}

