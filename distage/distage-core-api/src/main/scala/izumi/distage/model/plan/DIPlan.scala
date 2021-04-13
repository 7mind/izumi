package izumi.distage.model.plan

import izumi.distage.model.{Locator, PlannerInput}
import izumi.distage.model.definition.{Identifier, ModuleBase}
import izumi.distage.model.plan.operations.OperationOrigin
import izumi.distage.model.plan.repr.{DIPlanCompactFormatter, DepTreeRenderer}
import izumi.distage.model.plan.topology.DependencyGraph
import izumi.distage.model.reflection.{DIKey, SafeType}
import izumi.functional.Renderable
import izumi.fundamentals.graphs.{DG, GraphMeta}
import izumi.fundamentals.graphs.struct.IncidenceMatrix
import izumi.reflect.Tag

case class DIPlan(plan: DG[DIKey, ExecutableOp], input: PlannerInput)

object DIPlan {
  def empty: DIPlan = DIPlan(
    DG(IncidenceMatrix.empty, IncidenceMatrix.empty, GraphMeta.empty),
    PlannerInput.everything(ModuleBase.empty),
  )
  @inline implicit final def defaultFormatter: Renderable[DIPlan] = DIPlanCompactFormatter

  implicit class DIPlanSyntax(plan: DIPlan) {
    def keys: Set[DIKey] = plan.plan.meta.nodes.keySet
    def steps: List[ExecutableOp] = plan.plan.meta.nodes.values.toList
    def definition: ModuleBase = {
      val userBindings = steps.flatMap {
        op =>
          op.origin.value match {
            case OperationOrigin.UserBinding(binding) =>
              Seq(binding)
            case _ =>
              Seq.empty
          }
      }.toSet
      ModuleBase.make(userBindings)
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

    def render()(implicit ev: Renderable[DIPlan]): String = ev.render(plan)
    def renderDeps(key: DIKey): String = {
      new DepTreeRenderer(dg.tree(key), plan.plan.meta.nodes).render()
    }
    def renderAllDeps(): String = {
      val effectiveRoots = plan.plan.roots
      effectiveRoots.map(renderDeps).mkString("\n")
    }
    private lazy val dg = new DependencyGraph(plan.plan.predecessors.links, DependencyGraph.DependencyKind.Depends)
  }
}
