package izumi.distage.model.planning

import izumi.distage.model.plan.OrderedPlan

trait ForwardingRefResolver {
  def resolve(operations: OrderedPlan): OrderedPlan
}
