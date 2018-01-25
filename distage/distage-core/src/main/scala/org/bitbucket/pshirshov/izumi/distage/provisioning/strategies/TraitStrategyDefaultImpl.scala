package org.bitbucket.pshirshov.izumi.distage.provisioning.strategies

import java.lang.reflect.Method

import org.bitbucket.pshirshov.izumi.distage.TypeFull
import org.bitbucket.pshirshov.izumi.distage.commons.{ReflectionUtil, TraitTools}
import org.bitbucket.pshirshov.izumi.distage.model.plan.Association
import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.WiringOp
import org.bitbucket.pshirshov.izumi.distage.provisioning.cglib.{CgLibTraitMethodInterceptor, CglibTools}
import org.bitbucket.pshirshov.izumi.distage.provisioning.{OpResult, ProvisioningContext}

import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe._

case class TraitField(name: String)

case class TraitIndex(
                       methods: Map[Method, Association.Method]
                       , getters: Map[String, TraitField]
                       , setters: Map[String, TraitField]
                     )

class TraitStrategyDefaultImpl extends TraitStrategy {
  def makeTrait(context: ProvisioningContext, t: WiringOp.InstantiateTrait): Seq[OpResult] = {
    val traitDeps = context.narrow(t.wiring.associations.map(_.wireWith).toSet)

    val wiredMethodIndex = TraitStrategyDefaultImpl.traitIndex(t.wiring.instanceType, t.wiring.associations)

    val instanceType = t.wiring.instanceType
    val runtimeClass = currentMirror.runtimeClass(instanceType.tpe)
    val dispatcher = new CgLibTraitMethodInterceptor(wiredMethodIndex, traitDeps)

    CglibTools.mkdynamic(dispatcher, runtimeClass, t) {
      instance =>
        TraitTools.initTrait(instanceType, runtimeClass, instance)
        Seq(OpResult.NewInstance(t.target, instance))
    }
  }


}

object TraitStrategyDefaultImpl {
  final val instance = new TraitStrategyDefaultImpl()

  private def makeIndex(t: Seq[Association.Method]): Map[Method, Association.Method] = {
    t.map {
      m =>
        ReflectionUtil.toJavaMethod(m.context.definingClass, m.symbol) -> m
    }.toMap
  }

  def traitIndex(tpe: TypeFull, t: Seq[Association.Method]): TraitIndex = {
    val vals = tpe.tpe.decls.collect {
      case m: TermSymbol if m.isVal || m.isVar =>
        m
    }

    val getters = vals.map{ v =>
      v.name.toString -> TraitField(v.name.toString)
    }.toMap
    
    val setters = vals.map {
      v =>
        val runtimeClass = tpe.tpe.typeSymbol.asClass.fullName
        val setterBase = runtimeClass.replace('.', '$')
        val setterName = s"$setterBase$$_setter_$$${v.name.toString}_$$eq"
        setterName -> TraitField(v.name.toString)
    }.toMap

    TraitIndex(makeIndex(t), getters, setters)
  }
}

