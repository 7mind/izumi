package izumi.distage.model.definition

import izumi.distage.model.reflection.IdContract
import scala.language.implicitConversions

final case class ContractedId[I](id: I, contract: IdContract[I])

object ContractedId {
  implicit def fromIdContract[I: IdContract](id: I): ContractedId[I] = ContractedId(id, IdContract[I])
}
