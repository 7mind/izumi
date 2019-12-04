package izumi.distage.model.provisioning.strategies

trait FactoryExecutor {
  def execute(methodId: Int, args: Seq[Any]): Any
}
