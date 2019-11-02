package izumi.idealingua.model.il.ast.typed

import izumi.idealingua.model.common.TypeId.StreamsId

final case class Streams(id: StreamsId, streams: List[TypedStream], meta: NodeMeta)
