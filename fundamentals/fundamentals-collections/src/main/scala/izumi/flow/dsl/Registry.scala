package izumi.flow.dsl

trait Registry {
  def node(id: ValueId): NodeSimulator
}
