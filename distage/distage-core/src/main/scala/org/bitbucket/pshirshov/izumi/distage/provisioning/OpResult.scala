package org.bitbucket.pshirshov.izumi.distage.provisioning

import org.bitbucket.pshirshov.izumi.distage.model.DIKey

import scala.collection.mutable

sealed trait OpResult {}

object OpResult {

  case class NewInstance(key: DIKey, value: Any) extends OpResult

  case class NewImport(key: DIKey, value: Any) extends OpResult

  case class SetElement(set: mutable.HashSet[Any], instance: Any) extends OpResult
}