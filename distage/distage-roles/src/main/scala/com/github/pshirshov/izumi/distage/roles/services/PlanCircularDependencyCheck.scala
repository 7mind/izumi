package com.github.pshirshov.izumi.distage.roles.services

import com.github.pshirshov.izumi.distage.model.plan.ExecutableOp
import com.github.pshirshov.izumi.distage.roles.services.ModuleProviderImpl.ContextOptions
import com.github.pshirshov.izumi.logstage.api.IzLogger
import distage.OrderedPlan

class PlanCircularDependencyCheck(
                                   options: ContextOptions,
                                   logger: IzLogger,
                                 ) {
  def verify(plan: OrderedPlan): Unit = {
    if (options.warnOnCircularDeps) {
      val allProxies = plan.steps.collect {
        case s: ExecutableOp.ProxyOp.MakeProxy =>
          s
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
