package izumi.distage.model.provisioning

import izumi.distage.model.provisioning.PlanInterpreter.Finalizer
import izumi.distage.model.provisioning.Provision.ProvisionImmutable
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.DIKey

import scala.annotation.nowarn
import scala.collection.{Map, Seq, immutable, mutable}

trait Provision[+F[_]] {
  /**
    * This is an ordered collection!
    *
    * @note There is a [[scala.collection.SeqMap]] interface in Scala 2.13 but we need to stick to generic one because of 2.12
    */
  def instances: Map[DIKey, Any]
  def imports: Map[DIKey, Any]
  def finalizers: Seq[Finalizer[F]]

  def narrow(allRequiredKeys: Set[DIKey]): ProvisionImmutable[F]

  final def get(key: DIKey): Option[Any] = {
    instances.get(key).orElse(imports.get(key))
  }

  @nowarn("msg=Unused import")
  def enumerate: immutable.Seq[IdentifiedRef] = {
    import scala.collection.compat._
    instances.map(IdentifiedRef.tupled).to(scala.collection.immutable.Seq)
  }
  def index: immutable.Map[DIKey, Any] = {
    enumerate.map(i => i.key -> i.value).toMap
  }
}

object Provision {

  final case class ProvisionMutable[F[_]]() extends Provision[F] {
    override val instances: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any]()
    override val imports: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any]()
    override val finalizers: mutable.ListBuffer[Finalizer[F]] = mutable.ListBuffer[Finalizer[F]]()

    def toImmutable: ProvisionImmutable[F] = {
      ProvisionImmutable(instances, imports, finalizers)
    }

    override def narrow(allRequiredKeys: Set[DIKey]): ProvisionImmutable[F] = {
      toImmutable.narrow(allRequiredKeys)
    }
  }

  final case class ProvisionImmutable[+F[_]](
    // LinkedHashMap for ordering
    instancesImpl: mutable.LinkedHashMap[DIKey, Any],
    imports: Map[DIKey, Any],
    finalizers: Seq[Finalizer[F]],
  ) extends Provision[F] {
    override def instances: Map[DIKey, Any] = instancesImpl
    override lazy val enumerate: immutable.Seq[IdentifiedRef] = super.enumerate
    override lazy val index: immutable.Map[DIKey, Any] = super.index

    @nowarn("msg=Unused import")
    override def narrow(allRequiredKeys: Set[DIKey]): ProvisionImmutable[F] = {
      import scala.collection.compat._
      ProvisionImmutable(
        instancesImpl.filter(kv => allRequiredKeys.contains(kv._1)), // 2.13 compat
        imports.view.filterKeys(allRequiredKeys.contains).toMap, // 2.13 compat
        finalizers.filter(allRequiredKeys contains _.key),
      )
    }
  }

}
