package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.cglib.{CgLibTraitMethodInterceptor, CglibTools}
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

import scala.language.reflectiveCalls
import scala.reflect.runtime.{currentMirror, universe}

class TraitStrategyDefaultImpl extends TraitStrategy {
  def makeTrait(context: ProvisioningContext, t: WiringOp.InstantiateTrait): Seq[OpResult] = {
    val traitDeps = context.narrow(t.wiring.associations.map(_.wireWith).toSet)

    val wiredMethodIndex = t.wiring.associations.map {
      m =>
        // https://stackoverflow.com/questions/16787163/get-a-java-lang-reflect-method-from-a-reflect-runtime-universe-methodsymbol
        val method = m.symbol.asMethod
        val runtimeClass = currentMirror.runtimeClass(m.context.definingClass.tpe)
        val mirror = universe.runtimeMirror(runtimeClass.getClassLoader)
        val privateMirror = mirror.asInstanceOf[ {
          def methodToJava(sym: scala.reflect.internal.Symbols#MethodSymbol): java.lang.reflect.Method
        }]
        val javaMethod = privateMirror.methodToJava(method.asInstanceOf[scala.reflect.internal.Symbols#MethodSymbol])
        javaMethod -> m

    }.toMap

    val instanceType = t.wiring.instanceType
    val runtimeClass = currentMirror.runtimeClass(instanceType.tpe)
    val dispatcher = new CgLibTraitMethodInterceptor(wiredMethodIndex, traitDeps)

    CglibTools.mkdynamic(dispatcher, instanceType, runtimeClass, t) {
      instance =>
        TraitTools.initTrait(instanceType, runtimeClass, instance)
        Seq(OpResult.NewInstance(t.target, instance))
    }
  }
}

object TraitStrategyDefaultImpl {
  final val instance = new TraitStrategyDefaultImpl()
}
