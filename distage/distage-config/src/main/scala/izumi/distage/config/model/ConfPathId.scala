package izumi.distage.config.model

import izumi.distage.model.reflection.universe.RuntimeDIUniverse.{IdContract, IdContractImpl}

final case class ConfPathId(path: String)
object ConfPathId {
  implicit val idContract: IdContract[ConfPathId] = new IdContractImpl[ConfPathId]
}
