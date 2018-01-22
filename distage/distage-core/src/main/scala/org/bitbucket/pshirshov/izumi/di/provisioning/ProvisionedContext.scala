package org.bitbucket.pshirshov.izumi.di.provisioning

import org.bitbucket.pshirshov.izumi.di.model.DIKey

import scala.collection.Map

case class ProvisionedContext
(
  instances: Map[DIKey, Any]
  , imports: Map[DIKey, Any]
) extends Provision
