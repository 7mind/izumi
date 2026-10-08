package izumi.fundamentals.platform.uuid

import izumi.fundamentals.testkit.AnyWordSpec

import java.nio.ByteBuffer
import java.util.UUID

class IzUUIDTest extends AnyWordSpec {

  private final val UUIDByteSize = 16

  "IzUUID" should {

    "generate a time UUID" in assertTimeUUID(IzUUID.generateTimeUUID())

    "generate time UUID bytes" in assertUUIDBytes(IzUUID.generateTimeUUIDBytes())

    "create time UUID from timestamp" in assertTimeUUID(IzUUID.getTimeUUID(System.currentTimeMillis()))

    "create time UUID from microseconds" in assertTimeUUID(IzUUID.getTimeUUIDFromMicros(System.currentTimeMillis() * 1000))

    "create random time UUID from microseconds" in {
      val nowMicros = System.currentTimeMillis() * 1000
      val uuid1 = IzUUID.getRandomTimeUUIDFromMicros(nowMicros)
      val uuid2 = IzUUID.getRandomTimeUUIDFromMicros(nowMicros)
      assert(uuid1 != null)
      assert(uuid2 != null)
      assert(uuid1 != uuid2)
    }

    "create time UUID with nanos" in assertTimeUUID(IzUUID.getTimeUUID(System.currentTimeMillis(), 5000L))

    "create time UUID with nanos and clockSeqAndNode" in assertTimeUUID(IzUUID.getTimeUUID(System.currentTimeMillis(), 5000L, 0x123456789ABCL))

    "parse UUID from ByteBuffer" in {
      val original = IzUUID.generateTimeUUID()
      val parsed = IzUUID.getUUID(ByteBuffer.wrap(IzUUID.decompose(original)))
      assert(parsed == original)
    }

    "decompose UUID to bytes" in assertUUIDBytes(IzUUID.decompose(IzUUID.generateTimeUUID()))

    "generate minTimeUUID" in assertTimeUUID(IzUUID.minTimeUUID(System.currentTimeMillis()))

    "generate maxTimeUUID" in assertTimeUUID(IzUUID.maxTimeUUID(System.currentTimeMillis()))

    "min and max UUIDs should be ordered correctly" in {
      val now = System.currentTimeMillis()
      val minUuid = IzUUID.minTimeUUID(now)
      val maxUuid = IzUUID.maxTimeUUID(now)
      assert(minUuid.timestamp() <= maxUuid.timestamp())
    }

    "extract unix timestamp from UUID" in {
      val now = System.currentTimeMillis()
      val uuid = IzUUID.getTimeUUID(now)
      val extracted = IzUUID.unixTimestamp(uuid)
      assert(math.abs(extracted - now) < 1000)
    }

    "extract microseconds timestamp from UUID" in {
      val now = System.currentTimeMillis()
      val nowMicros = now * 1000
      val uuid = IzUUID.getTimeUUID(now)
      val extracted = IzUUID.microsTimestamp(uuid)
      assert(math.abs(extracted - nowMicros) < 1000000)
    }

    "get time UUID bytes from millis" in assertUUIDBytes(IzUUID.getTimeUUIDBytes(System.currentTimeMillis()))

    "get time UUID bytes from millis and nanos" in assertUUIDBytes(IzUUID.getTimeUUIDBytes(System.currentTimeMillis(), 5000))

    "reject invalid nanos in getTimeUUIDBytes" in {
      val now = System.currentTimeMillis()
      assertThrows[IllegalArgumentException] {
        IzUUID.getTimeUUIDBytes(now, 10000)
      }
    }

    "get adjusted timestamp" in {
      val now = System.currentTimeMillis()
      val uuid = IzUUID.getTimeUUID(now)
      val adjusted = IzUUID.getAdjustedTimestamp(uuid)
      assert(math.abs(adjusted - now) < 1000)
    }

    "generate unique UUIDs in sequence" in {
      val uuids = (1 to 100).map(_ => IzUUID.generateTimeUUID())
      assert(uuids.toSet.size == 100)
    }

    "generate monotonically increasing UUIDs" in {
      val uuids = (1 to 100).map(_ => IzUUID.generateTimeUUID())
      val timestamps = uuids.map(_.timestamp())
      assert(timestamps == timestamps.sorted)
    }

  }

  private def assertUUIDBytes(bytes: Array[Byte]): Unit = assert(bytes.length == UUIDByteSize)

  private def assertTimeUUID(uuid: UUID): Unit = {
    assert(uuid != null)
    assert(uuid.version() == 1)
  }
}
