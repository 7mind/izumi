package com.github.pshirshov.izumi.distage.model.provisioning

import com.github.pshirshov.izumi.distage.model.references.IdentifiedRef
import com.github.pshirshov.izumi.distage.model.reflection.universe.RuntimeDIUniverse.DIKey

import scala.collection.{Map, Seq, mutable}

trait Provision[+F[_]] {
  def instances: Map[DIKey, Any]
  def imports: Map[DIKey, Any]
  // FIXME: think of a better place ???
  def finalizers: Seq[(DIKey, () => F[Unit])]

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
    instances: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any]()
    , imports: mutable.LinkedHashMap[DIKey, Any] = mutable.LinkedHashMap[DIKey, Any]()
    , finalizers: mutable.ListBuffer[(DIKey, () => F[Unit])] = mutable.ListBuffer[(DIKey, () => F[Unit])]()
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
     instances: Map[DIKey, Any]
     , imports: Map[DIKey, Any]
     , finalizers: Seq[(DIKey, () => F[Unit])]
   ) extends Provision[F] {
     override def narrow(allRequiredKeys: Set[DIKey]): Provision[F] = {
       ProvisionImmutable(
         instances.filterKeys(allRequiredKeys.contains).toMap // 2.13 compat
         , imports.filterKeys(allRequiredKeys.contains).toMap // 2.13 compat
         , finalizers.filter(allRequiredKeys contains _._1)
       )
     }

     override def extend(values: Map[DIKey, Any]): Provision[F] = {
       ProvisionImmutable(
         instances ++ values
         , imports
         , finalizers
       )
     }
   }

}
