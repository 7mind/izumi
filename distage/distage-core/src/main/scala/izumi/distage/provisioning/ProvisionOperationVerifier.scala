package izumi.distage.provisioning

import izumi.distage.model.exceptions.interpretation.ProvisionerIssue
import izumi.distage.model.provisioning.proxies.ProxyDispatcher
import izumi.distage.model.provisioning.proxies.ProxyDispatcher.ByNameDispatcher
import izumi.distage.model.reflection.{DIKey, MirrorProvider}
import izumi.fundamentals.platform.language.Quirks
import izumi.distage.model.exceptions.interpretation.ProvisionerIssue.*

trait ProvisionOperationVerifier {
  def verify(target: DIKey, prohibited: collection.Set[DIKey], value: Any, clue: String): Either[ProvisionerIssue, Unit]
}

object ProvisionOperationVerifier {

  final class Default(
    mirror: MirrorProvider
  ) extends ProvisionOperationVerifier {

    def verify(target: DIKey, keys: scala.collection.Set[DIKey], value: Any, clue: String): Either[ProvisionerIssue, Unit] = {
      for {
        _ <- if (keys.contains(target)) Left(DuplicateInstances(target)) else Right(())
        _ <- target match {
          case _: DIKey.ProxyControllerKey => // each proxy operation returns two assignments
            throwIfIncompatible(DIKey.get[ProxyDispatcher], clue, value)

          case _ =>
            value match {
              case d: ByNameDispatcher =>
                val dispatcherTypeCompatible = d.key.tpe == target.tpe || (d.key.tpe <:< target.tpe)

                if (!dispatcherTypeCompatible) {
                  Left(IncompatibleTypes(target, target.tpe, d.key.tpe))
                } else {
                  Right(())
                }

              case u =>
                throwIfIncompatible(target, clue, u)
            }
        }
      } yield {}

    }

    private def throwIfIncompatible(target: DIKey, clue: String, value: Any): Either[ProvisionerIssue, Unit] = {
      if (!mirror.runtimeClassCompatible(target.tpe, value)) {
        Left(IncompatibleRuntimeClass(target, value.getClass, clue))
      } else {
        Right(())
      }
    }

  }

  object Null extends ProvisionOperationVerifier {
    override def verify(target: DIKey, keys: collection.Set[DIKey], value: Any, clue: String): Either[ProvisionerIssue, Unit] = {
      Right(Quirks.discard(target, keys, value, clue))
    }
  }

}
