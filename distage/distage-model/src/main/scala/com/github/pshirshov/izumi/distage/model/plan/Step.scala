package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp.InstantiationOp
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse

final case class Step(wiring: RuntimeDIUniverse.Wiring, op: InstantiationOp)

//sealed trait Provisioning[G, B] {
//  @inline def map[G1](f: G => G1): Provisioning[G1, B]
//}

//object Provisioning {
//  type Diagnostics = Seq[ImplDef]
//  type StepProvisioning = Provisioning[NextOps, Diagnostics]
//  type InstanceProvisioning = Provisioning[Step, Diagnostics]
//
//  final case class Possible[G, B](possible: G) extends Provisioning[G, B] {
//    @inline override def map[G1](f: G => G1): Provisioning[G1, B] = Possible(f(possible))
//  }
//
//  final case class Impossible[G, B](impossible: B) extends Provisioning[G, B] {
//    @inline override def map[G1](f: G => G1): Provisioning[G1, B] = Impossible(impossible)
//  }
//
//}
