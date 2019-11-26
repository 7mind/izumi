package izumi.distage.testkit.services.st.adapter

import izumi.distage.model.definition.Axis.AxisValue
import izumi.distage.model.definition.AxisBase
import izumi.distage.roles.BootstrapConfig
import distage.SafeType

@deprecated("Use dstest", "2019/Jul/18")
sealed trait MemoizationContextId

object MemoizationContextId {

  case object Shared extends MemoizationContextId

  case class PerRuntimeAndActivationAndBsconfig[F[_]](bootstrapConfig: BootstrapConfig, activation: Map[AxisBase, AxisValue], fType: SafeType)
    extends MemoizationContextId

  case class Custom(id: String) extends MemoizationContextId

}
