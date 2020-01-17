package izumi.distage.testkit.services.scalatest.adapter

import distage.{Activation, SafeType}

@deprecated("Use dstest", "2019/Jul/18")
sealed trait MemoizationContextId

object MemoizationContextId {

  case object Shared extends MemoizationContextId

  case class PerRuntimeAndActivationAndBsconfig[F[_]](bootstrapConfig: BootstrapConfig, activation: Activation, fType: SafeType) extends MemoizationContextId

  case class Custom(id: String) extends MemoizationContextId

}
