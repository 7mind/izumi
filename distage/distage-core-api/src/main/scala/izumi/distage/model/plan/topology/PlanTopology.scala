package izumi.distage.model.plan.topology

import izumi.distage.model.plan.topology.DependencyGraph.DependencyKind
import izumi.distage.model.reflection._


/**
  * This class represents direct node dependencies and allows to retrive full transitive dependencies for a node
  */
sealed trait PlanTopology {
  def removeKeys(keys: Set[DIKey]): PlanTopology

  def dependees: DependencyGraph

  def dependencies: DependencyGraph

  /**
    * This method is relatively expensive
    *
    * @return full set of all the keys transitively depending on key
    */
  def transitiveDependees(key: DIKey): Set[DIKey] = dependees.transitive(key)

  /**
    * This method is relatively expensive
    *
    * @return full set of all the keys transitively depending on key
    */
  def transitiveDependencies(key: DIKey): Set[DIKey] = dependencies.transitive(key)

  def effectiveRoots: Set[DIKey] = {
    dependees.graph.toSeq.filter(_._2.isEmpty).map(_._1).toSet
  }
}

object PlanTopology {

  def empty: PlanTopologyImmutable = PlanTopologyImmutable(DependencyGraph(Map.empty, DependencyKind.Depends), DependencyGraph(Map.empty, DependencyKind.Required))

  final case class PlanTopologyImmutable(
                                          dependees: DependencyGraph,
                                          dependencies: DependencyGraph,
                                        ) extends PlanTopology {
    override def removeKeys(keys: Set[DIKey]): PlanTopology = {
      PlanTopologyImmutable(
        dependees.dropDepsOf(keys),
        dependencies.dropDepsOf(keys),
      )
    }
  }

}
