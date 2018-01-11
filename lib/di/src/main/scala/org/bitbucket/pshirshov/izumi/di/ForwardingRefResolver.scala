package org.bitbucket.pshirshov.izumi.di

import org.bitbucket.pshirshov.izumi.di.model.DIKey
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyOp.Statement
import org.bitbucket.pshirshov.izumi.di.model.plan.DodgyPlan
import org.bitbucket.pshirshov.izumi.di.model.plan.ExecutableOp.{DependentOp, InitProxies, MakeProxy}

import scala.collection.mutable

trait ForwardingRefResolver {
  def resolve(steps: DodgyPlan): DodgyPlan
}

class ForwardingRefResolverDefaultImpl extends ForwardingRefResolver {
  override def resolve(plan: DodgyPlan): DodgyPlan = {
    val forwardReferences = plan.steps.foldLeft(new mutable.HashMap[DIKey, mutable.Set[DIKey]]) {
      case (acc, Statement(op: DependentOp)) =>
        val forwardRefs: Set[DIKey] = op.deps.map(_.wireWith).filterNot(acc.contains).toSet
        acc.getOrElseUpdate(op.target, mutable.Set.empty) ++= forwardRefs
        acc

      case (acc, Statement(op)) =>
        acc.getOrElseUpdate(op.target, mutable.Set.empty)
        acc

      case (acc, _) =>
        acc
    }.filter(_._2.nonEmpty)

    val forwarded = forwardReferences.foldLeft(new mutable.HashMap[DIKey, mutable.Set[DIKey]] with mutable.MultiMap[DIKey, DIKey]) {
      case (acc, (reference, referencee)) =>
        referencee.foreach(acc.addBinding(_, reference))
        acc
    }

    System.err.println(s"Forward refs: $forwardReferences, inversed: $forwarded")
    val resolvedSteps = plan.steps.flatMap {
      case Statement(step) if forwardReferences.contains(step.target) =>
        Seq(Statement(MakeProxy(step, forwardReferences(step.target).toSet)))

      case s@Statement(step) if forwarded.contains(step.target) =>
        Seq(Statement(InitProxies(step, forwarded(step.target).toSet)))


      case step =>
        Seq(step)
    }
    DodgyPlan(resolvedSteps)
  }
}