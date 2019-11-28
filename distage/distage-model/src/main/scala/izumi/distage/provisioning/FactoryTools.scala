package izumi.distage.provisioning

import izumi.distage.model.exceptions.UnexpectedProvisionResultException
import izumi.distage.model.provisioning.NewObjectOp
import izumi.distage.model.provisioning.NewObjectOp.{NewImport, NewInstance}

object FactoryTools {

  def interpret(results: Seq[NewObjectOp]): AnyRef = {
    results.toList match {
      case List(i: NewInstance) =>
        i.instance.asInstanceOf[AnyRef]
      case List(i: NewImport) =>
        i.instance.asInstanceOf[AnyRef]
      case List(_) =>
        throw new UnexpectedProvisionResultException(
          s"Factory returned a result class other than NewInstance or NewImport in $results", results)
      case _ :: _ =>
        throw new UnexpectedProvisionResultException(
          s"Factory returned more than one result in $results", results)
      case Nil =>
        throw new UnexpectedProvisionResultException(
          s"Factory empty result list: $results", results)
    }
  }

}
