package izumi.fundamentals.platform.uuid

import java.util.UUID

object IzUUID extends UUIDGen {
  final val Zero: UUID = new UUID(0L, 0L)
}
