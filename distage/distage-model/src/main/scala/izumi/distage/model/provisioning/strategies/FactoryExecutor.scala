package izumi.distage.model.provisioning.strategies

import izumi.distage.model.provisioning.NewObjectOp

trait FactoryExecutor {
  type MethodId = Int

  def execute(methodId: MethodId, args: Seq[Any]): Seq[NewObjectOp]
}
