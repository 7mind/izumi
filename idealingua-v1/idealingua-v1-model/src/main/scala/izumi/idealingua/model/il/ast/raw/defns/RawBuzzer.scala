package izumi.idealingua.model.il.ast.raw.defns

import izumi.idealingua.model.common.TypeId.BuzzerId

final case class RawBuzzer(id: BuzzerId, events: List[RawMethod], meta: RawNodeMeta)
