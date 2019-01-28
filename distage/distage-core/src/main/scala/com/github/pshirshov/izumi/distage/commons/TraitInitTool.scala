package com.github.pshirshov.izumi.distage.commons

import java.lang.reflect.{InvocationTargetException, Method}

import com.github.pshirshov.izumi.distage.model.exceptions.TraitInitializationFailedException
import com.github.pshirshov.izumi.distage.model.provisioning.strategies.{TraitField, TraitIndex}
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse
import com.github.pshirshov.izumi.fundamentals.reflection._

class TraitInitTool {

  def traitIndex(tpe: RuntimeDIUniverse.SafeType, methods: Seq[RuntimeDIUniverse.Association.AbstractMethod]): TraitIndex = {
    val vals = tpe.tpe.decls.collect {
      case m: RuntimeDIUniverse.u.TermSymbolApi if m.isVal || m.isVar =>
        m
    }

    val getters = vals.map { v =>
      v.name.toString -> TraitField(v.name.toString)
    }.toMap

    val setters = vals.map {
      v =>
        val runtimeClass = tpe.tpe.typeSymbol.asClass.fullName
        val setterBase = runtimeClass.replace('.', '$')
        val setterName = s"$setterBase$$_setter_$$${v.name.toString}_$$eq"
        setterName -> TraitField(v.name.toString)
    }.toMap

    TraitIndex(makeTraitIndex(methods), getters, setters)
  }

  def initTrait(instanceType: RuntimeDIUniverse.SafeType, runtimeClass: Class[_], instance: AnyRef): Unit = {
    instanceType.tpe.decls.find(_.name.decodedName.toString == "$init$") match {
      case Some(_) => // here we have an instance of scala MethodSymbol though we can't reflect it, so let's use java
        try {
          try {
            val initMethod = runtimeClass
              .getDeclaredMethod("$init$", runtimeClass)
            initMethod.invoke(instance, instance)
            ()
          } catch {
            case e: InvocationTargetException =>
              throw e.getCause
          }
        } catch {
          case e: AbstractMethodError =>
            throw new TraitInitializationFailedException(s"TODO: Failed to initialize trait $instanceType. Probably it contains fields (val or var) though fields are not supported yet, see https://github.com/pshirshov/izumi-r2/issues/26", instanceType, e)

          case e: Throwable =>
            throw new TraitInitializationFailedException(s"Failed to initialize trait $instanceType. It may be an issue with the trait, framework bug or trait instantiator implemetation lim", instanceType, e)
        }
      case None =>
    }
  }

  private def makeTraitIndex(methods: Seq[RuntimeDIUniverse.Association.AbstractMethod]): Map[Method, RuntimeDIUniverse.Association.AbstractMethod] = {
    methods.map {
      m =>
        ReflectionUtil.toJavaMethod(m.context.definingClass.tpe, m.context.methodSymbol.underlying) -> m
    }.toMap
  }

}
