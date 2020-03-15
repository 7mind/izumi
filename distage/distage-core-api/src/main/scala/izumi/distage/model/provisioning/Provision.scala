package izumi.distage.model.provisioning

import izumi.distage.model.provisioning.PlanInterpreter.Finalizer
import izumi.distage.model.references.IdentifiedRef
import izumi.distage.model.reflection.DIKey

import scala.collection.{Map, Seq, mutable}

trait Provision[+F[_]] {
  def instances: Map[DIKey, Any]
  def imports: Map[DIKey, Any]
  def finalizers: Seq[Finalizer[F]]

  def narrow(allRequiredKeys: Set[DIKey]): Provision[F]
  def extend(values: Map[DIKey, Any]): Provision[F]

  final def get(key: DIKey): Option[Any] = {
    instances.get(key).orElse(imports.get(key))
  }

  final def enumerate: Seq[IdentifiedRef] = instances.map(IdentifiedRef.tupled).toSeq
}

object Provision {

  final case class ProvisionMutable[F[_]]
  (
    instances: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any](),
    imports: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any](),
    finalizers: mutable.ListBuffer[Finalizer[F]] = mutable.ListBuffer[Finalizer[F]](),
  ) extends Provision[F] {
    def toImmutable: ProvisionImmutable[F] = {
      ProvisionImmutable(instances, imports, finalizers)
    }

    override def narrow(allRequiredKeys: Set[DIKey]): Provision[F] = {
      toImmutable.narrow(allRequiredKeys)
    }

    override def extend(values: collection.Map[DIKey, Any]): Provision[F] = {
      toImmutable.extend(values)
    }
  }

  final case class ProvisionImmutable[+F[_]]
   (
     instances: Map[DIKey, Any],
     imports: Map[DIKey, Any],
     finalizers: Seq[Finalizer[F]],
   ) extends Provision[F] {
     override def narrow(allRequiredKeys: Set[DIKey]): Provision[F] = {
       ProvisionImmutable(
         instances.filterKeys(allRequiredKeys.contains).toMap, // 2.13 compat
         imports.filterKeys(allRequiredKeys.contains).toMap, // 2.13 compat
         finalizers.filter(allRequiredKeys contains _.key),
       )
     }

     override def extend(values: Map[DIKey, Any]): Provision[F] = {
       ProvisionImmutable(
         instances ++ values,
         imports,
         finalizers,
       )
     }
   }

}
