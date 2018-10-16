package com.github.pshirshov.izumi.distage.planning

import com.github.pshirshov.izumi.distage.model.plan.{ExecutableOp, OrderedPlan, SemiPlan}
import com.github.pshirshov.izumi.distage.model.planning.PlanningHook
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, SafeType, Tag, Wiring}

import scala.collection.immutable.ListSet

/**
  * A hook that will collect all bindings to types {{{<: T}}} into a `Set[T]` available for injection as a dependency.
  *
  * Usage:
  *
  * {{{
  *   val collectCloseables = new AssignableFromAutoSetHook[AutoCloseable]
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
  * */
class AssignableFromAutoSetHook[T: Tag] extends PlanningHook {
  protected val setElemetType: SafeType = SafeType.get[T]

  protected val setKey: DIKey = DIKey.get[Set[T]]

  override def phase50PreForwarding(plan: SemiPlan): SemiPlan = {
    val newMembers = scala.collection.mutable.ArrayBuffer[DIKey.SetElementKey]()

    val newSteps = plan.steps.flatMap {
      op =>
        val opTargetType = ExecutableOp.instanceType(op)
        if (opTargetType weak_<:< setElemetType) {
          val elementKey = DIKey.SetElementKey(setKey, op.target.tpe, plan.steps.size + newMembers.size)
          newMembers += elementKey
          Seq(op, ExecutableOp.WiringOp.ReferenceKey(elementKey, Wiring.UnaryWiring.Reference(op.target.tpe, op.target, weak = true), op.origin))
        } else if (op.target == setKey) {
          Seq.empty
        } else {
          Seq(op)
        }
    }

    val newSetKeys: scala.collection.immutable.Set[DIKey] = ListSet(newMembers: _*) // newMembers.toSet
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
