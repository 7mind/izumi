package org.bitbucket.pshirshov.izumi.distage.model.plan

import org.bitbucket.pshirshov.izumi.distage.model.plan.ExecutableOp.InstantiationOp



case class Step(wiring: Wiring, ops: Seq[InstantiationOp])

//sealed trait Provisioning[G, B] {
//  @inline def map[G1](f: G => G1): Provisioning[G1, B]
//}

//object Provisioning {
//  type Diagnostics = Seq[ImplDef]
//  type StepProvisioning = Provisioning[NextOps, Diagnostics]
//  type InstanceProvisioning = Provisioning[Step, Diagnostics]
//
//  case class Possible[G, B](possible: G) extends Provisioning[G, B] {
//    @inline override def map[G1](f: G => G1): Provisioning[G1, B] = Possible(f(possible))
//  }
//
//  case class Impossible[G, B](impossible: B) extends Provisioning[G, B] {
//    @inline override def map[G1](f: G => G1): Provisioning[G1, B] = Impossible(impossible)
//  }
//
//}