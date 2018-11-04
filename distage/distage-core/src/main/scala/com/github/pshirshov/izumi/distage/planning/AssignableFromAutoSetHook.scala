package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, OrderedPlan, SemiPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.providers.ProviderMagnet
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse._

import scala.collection.immutable.ListSet

/**
  * A hook that will collect all bindings to types {{{<: T}}} into a `Set[T]` available for injection as a dependency.
  *
  * Usage:
  *
  * {{{
  *   val collectCloseables = new AssignableFromAutoSetHook[AutoCloseable, AutoCloseable](identity)
  *
  *   val injector = Injector(new BootstrapModuleDef {
  *     many[PlanningHook]
  *       .add(collectCloseables)
  *   })
  * }}}
  *
  * Then, in any class created from `injector`:
  *
  * {{{
  *   class App(allCloseables: Set[AutoCloseable]) {
  *     ...
  *   }
  * }}}
  *
  * These Auto-Sets can be used to implement custom lifetimes:
  *
  * {{{
  *   val locator = injector.produce(modules)
  *
  *   val closeables = locator.get[Set[AutoCloseable]]
  *   try { locator.get[App].runMain() } finally {
  *     // reverse closeables list, Auto-Sets preserve order, in the order of *initialization*
  *     // Therefore resources should closed in the *opposite order*
  *     // i.e. if C depends on B depends on A, create: A -> B -> C, close: C -> B -> A
  *     closeables.reverse.foreach(_.close())
  *   }
  * }}}
  *
  * Auto-Sets are NOT subject to [[gc.TracingGcModule Garbage Collection]], they are assembled *after* garbage collection is done,
  * as such they can't contain garbage by construction.
  *
  **/
class AssignableFromAutoSetHook[INSTANCE: Tag, BINDING: Tag](private val wrap: INSTANCE => BINDING) extends PlanningHook {
  protected val instanceType: SafeType = SafeType.get[INSTANCE]
  protected val setElemetType: SafeType = SafeType.get[BINDING]
  protected val setKey: DIKey = DIKey.get[Set[BINDING]]


  override def phase45PreForwardingCleanup(plan: SemiPlan): SemiPlan = {
    val cleaned = plan.steps.flatMap {
      op =>
        op.target match {
          // we should throw out all existing elements of the set
          case `setKey` =>
            Seq.empty

          // and we should throw out existing set definition
          case s: DIKey.SetElementKey if s.set == setKey =>
            Seq.empty

          case _ =>
            Seq(op)
        }
    }

    SemiPlan(plan.definition, cleaned)
  }

  override def phase50PreForwarding(plan: SemiPlan): SemiPlan = {
    val newMembers = scala.collection.mutable.ArrayBuffer[DIKey]()

    val newSteps = plan.steps.flatMap {
      op =>
        op.target match {
          case `setKey` =>
            op match {
              case op: ExecutableOp.CreateSet =>
                newMembers ++= op.members
                Seq.empty
              case _ =>
                Seq.empty
            }

          case s: DIKey.SetElementKey if s.set == setKey =>
            Seq(op)

          case _ if ExecutableOp.instanceType(op) weak_<:< instanceType =>
            if (instanceType == setElemetType) {
              newMembers += op.target
              Seq(op)
            } else {
              val newKey = DIKey.SetElementKey(setKey, op.target)
              val provider = ProviderMagnet(wrap).get
              val newOp = ExecutableOp.WiringOp.CallProvider(newKey, Wiring.UnaryWiring.Function(provider, provider.associations), op.origin)
              newMembers += newKey
              Seq(op, newOp)
            }

          case _ =>
            Seq(op)
        }
    }

    val newSetKeys: scala.collection.immutable.Set[DIKey] = ListSet(newMembers: _*)
    val newSetOp = ExecutableOp.CreateSet(setKey, setKey.tpe, newSetKeys, None)
    SemiPlan(plan.definition, newSteps :+ newSetOp)
  }

  override def phase90AfterForwarding(plan: OrderedPlan): OrderedPlan = {
    val allKeys = plan.steps.map(_.target).to[ListSet]

    val withReorderedSetElements = plan.steps.map {
      case op@ExecutableOp.CreateSet(`setKey`, _, newSetKeys, _) =>
        // now reorderedKeys has exactly same elements as newSetKeys but in instantiation order
        val reorderedKeys = allKeys.intersect(newSetKeys)
        op.copy(members = reorderedKeys)

      case op =>
        op
    }

    plan.copy(steps = withReorderedSetElements)
  }
}
