package izumi.distage.model.plan

import izumi.distage.model.Locator
import izumi.distage.model.definition.Identifier
import izumi.distage.model.plan.ExecutableOp.{ImportDependency, SemiplanOp}
import izumi.distage.model.reflection._
import izumi.reflect.Tag

private[plan] trait AbstractPlanExtendedAPI[OpType <: ExecutableOp] extends Any { this: AbstractPlan[OpType] =>

  def resolveImports(f: PartialFunction[ImportDependency, Any]): AbstractPlan[OpType]
  def resolveImport[T: Tag](instance: T): AbstractPlan[OpType]
  def resolveImport[T: Tag](id: Identifier)(instance: T): AbstractPlan[OpType]

  /** Try to substitute unresolved dependencies in this plan by objects in `locator` */
  def locateImports(locator: Locator): AbstractPlan[OpType]
  def toSemi: SemiPlan

  /**
    * Get all imports (unresolved dependencies).
    *
    * Note, presence of imports does not *always* mean
    * that a plan is invalid, imports may be fulfilled by a parent
    * `Locator`, by BootstrapContext, or they may be materialized by
    * a custom [[izumi.distage.model.provisioning.strategies.ImportStrategy]]
    *
    * @see [[izumi.distage.model.plan.impl.OrderedPlanOps#assertValidOrThrow]] for a check you can use in tests
    */
  final def getImports: Seq[ImportDependency] =
    steps.collect { case i: ImportDependency => i }

  final def keys: Set[DIKey] = {
    steps.map(_.target).toSet
  }

  final def filter[T: Tag]: Seq[ExecutableOp] = {
    steps.filter(_.target == DIKey.get[T])
  }

  final def map(f: SemiplanOp => SemiplanOp): SemiPlan = {
    val SemiPlan(steps, gcMode) = toSemi
    SemiPlan(steps.map(f), gcMode)
  }

  final def flatMap(f: SemiplanOp => Seq[SemiplanOp]): SemiPlan = {
    val SemiPlan(steps, gcMode) = toSemi
    SemiPlan(steps.flatMap(f), gcMode)
  }

  final def collect(f: PartialFunction[SemiplanOp, SemiplanOp]): SemiPlan = {
    val SemiPlan(steps, gcMode) = toSemi
    SemiPlan(steps.collect(f), gcMode)
  }

  final def ++(that: AbstractPlan[OpType]): SemiPlan = {
    val SemiPlan(steps, gcMode) = toSemi
    val that0 = that.toSemi
    SemiPlan(steps ++ that0.steps, gcMode)
  }

  final def collectChildren[T: Tag]: Seq[ExecutableOp] = {
    val tpe = SafeType.get[T]
    steps.filter(op => op.instanceType <:< tpe)
  }

  final def collectChildrenKeys[T: Tag]: Set[DIKey] = {
    val tpe = SafeType.get[T]
    steps.iterator.collect {
      case op if op.instanceType <:< tpe => op.target
    }.toSet
  }

  final def collectChildrenKeysSplit[T1, T2](implicit t1: Tag[T1], t2: Tag[T2]): (Set[DIKey], Set[DIKey]) = {
    if (t1.tag == t2.tag) {
      (collectChildrenKeys[T1], Set.empty)
    } else {
      val tpe1 = SafeType.get[T1]
      val tpe2 = SafeType.get[T2]

      val res1 = Set.newBuilder[DIKey]
      val res2 = Set.newBuilder[DIKey]

      steps.foreach {
        op =>
          if (op.instanceType <:< tpe1) {
            res1 += op.target
          } else if (op.instanceType <:< tpe2) {
            res2 += op.target
          }
      }
      (res1.result(), res2.result())
    }
  }

  final def foldLeft[T](z: T, f: (T, ExecutableOp) => T): T = {
    steps.foldLeft(z)(f)
  }

}
