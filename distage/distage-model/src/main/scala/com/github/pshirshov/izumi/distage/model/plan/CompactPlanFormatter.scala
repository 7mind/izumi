package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.{DIKey, TypeNative}
import com.github.pshirshov.izumi.functional.Renderable

trait CompactPlanFormatter extends Renderable[OrderedPlan] {
  override def render(plan: OrderedPlan): String = {
    val minimizer = new KeyMinimizer(plan.keys)
    val tf: TypeFormatter = (key: TypeNative) => minimizer.renderType(key)

    val kf: KeyFormatter = (key: DIKey) => minimizer.render(key)

    val opFormatter = new OpFormatter.Impl(kf, tf)

    plan.steps.map(opFormatter.format).mkString("\n")
  }
}

object CompactPlanFormatter {
  implicit object OrderedPlanFormatter extends CompactPlanFormatter
}
