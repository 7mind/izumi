package izumi.distage.model.provisioning

import izumi.distage.model.provisioning.PlanInterpreter.Finalizer
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.DIKey

import scala.annotation.nowarn
import scala.collection.{Map, Seq, mutable}

trait Provision[+F[_]] {
  /**
    * This is an ordered collection!
    * There is [[scala.collection.SeqMap]] interface in Scala 2.13 but we need to stick to generic one because of 2.12
    */
  def instances: Map[DIKey, Any]
  def imports: Map[DIKey, Any]
  def finalizers: Seq[Finalizer[F]]

  def narrow(allRequiredKeys: Set[DIKey]): Provision[F]

  final def get(key: DIKey): Option[Any] = {
    instances.get(key).orElse(imports.get(key))
  }

  final def enumerate: Seq[IdentifiedRef] = instances.map(IdentifiedRef.tupled).toSeq
}

object Provision {

  final case class ProvisionMutable[F[_]]() extends Provision[F] {
    override val instances: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any]()
    override val imports: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any]()
    override val finalizers: mutable.ListBuffer[Finalizer[F]] = mutable.ListBuffer[Finalizer[F]]()

    @nowarn("msg=Unused import")
    def toImmutable: ProvisionImmutable[F] = {
      import scala.collection.compat._
      ProvisionImmutable(instances, imports, finalizers)
    }

    override def narrow(allRequiredKeys: Set[DIKey]): Provision[F] = {
      toImmutable.narrow(allRequiredKeys)
    }
  }

  final case class ProvisionImmutable[+F[_]](
    instancesImpl: mutable.LinkedHashMap[DIKey, Any],
    imports: Map[DIKey, Any],
    finalizers: Seq[Finalizer[F]],
  ) extends Provision[F] {

    override def instances: Map[DIKey, Any] = instancesImpl

    @nowarn("msg=Unused import")
    override def narrow(allRequiredKeys: Set[DIKey]): Provision[F] = {
      import scala.collection.compat._
      ProvisionImmutable(
        instances.view.filterKeys(allRequiredKeys.contains).to(mutable.LinkedHashMap), // 2.13 compa
        imports.view.filterKeys(allRequiredKeys.contains).toMap, // 2.13 compat
        finalizers.filter(allRequiredKeys contains _.key),
      )
    }
  }

}
