package com.github.pshirshov.izumi.distage.model.plan

import com.github.pshirshov.izumi.distage.model
import com.github.pshirshov.izumi.distage.model.reflection
import com.github.pshirshov.izumi.functional.Renderable


trait CompactPlanFormatter extends Renderable[OrderedPlan] {
  override def render(plan: OrderedPlan): String = {
    val minimizer = new KeyMinimizer(plan.keys)
    val tf = new TypeFormatter {
      override def format(key: reflection.universe.RuntimeDIUniverse.TypeNative): String = minimizer.renderType(key)

      override def format(key: Class[_]): String = minimizer.renderType(key)
    }

    val kf: KeyFormatter = (key: model.reflection.universe.RuntimeDIUniverse.DIKey) => minimizer.render(key)

    val opFormatter = new OpFormatter.Impl(kf, tf)

    plan.steps.map(opFormatter.format).mkString("\n")
  }

}

object CompactPlanFormatter {

  implicit object OrderedPlanFormatter extends CompactPlanFormatter

}
