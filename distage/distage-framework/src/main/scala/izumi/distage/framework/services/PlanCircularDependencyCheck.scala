package izumi.distage.framework.services

import distage.OrderedPlan
import izumi.distage.framework.config.PlanningOptions
import izumi.distage.model.plan.ExecutableOp
import izumi.logstage.api.IzLogger

class PlanCircularDependencyCheck(
                                   options: PlanningOptions,
                                   logger: IzLogger,
                                 ) {
  def verify(plan: OrderedPlan): Unit = {
    if (options.warnOnCircularDeps) {
      val allProxies = plan.steps.collect {
        case s: ExecutableOp.ProxyOp.MakeProxy if !s.byNameAllowed => s
      }

      allProxies.foreach {
        s =>
          val deptree = plan.topology.dependencies.tree(s.target)
          val tree = s"\n${plan.renderDeps(deptree)}"
          logger.warn(s"Circular dependency has been resolved with proxy for ${s.target -> "key"}, $tree")
      }
    }
  }
}
