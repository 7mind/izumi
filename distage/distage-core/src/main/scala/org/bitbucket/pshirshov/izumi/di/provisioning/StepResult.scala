package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.model.DIKey

import scala.collection.mutable

sealed trait StepResult {}

object StepResult {

  case class NewInstance(key: DIKey, value: Any) extends StepResult
  
  case class NewImport(key: DIKey, value: Any) extends StepResult

  case class SetElement(set: mutable.HashSet[Any], instance: Any) extends StepResult

}