package izumi.fundamentals.platform.uuid

import org.scalatest.wordspec.AnyWordSpec

import java.nio.ByteBuffer

class IzUUIDTest extends AnyWordSpec {

  "IzUUID" should {

    "generate a time UUID" in {
      val uuid = IzUUID.generateTimeUUID()
      assert(uuid != null)
      assert(uuid.version() == 1)
    }

    "generate time UUID bytes" in {
      val bytes = IzUUID.generateTimeUUIDBytes()
      assert(bytes.length == 16)
    }

    "create time UUID from timestamp" in {
      val now = System.currentTimeMillis()
      val uuid = IzUUID.getTimeUUID(now)
      assert(uuid != null)
      assert(uuid.version() == 1)
    }

    "create time UUID from microseconds" in {
      val nowMicros = System.currentTimeMillis() * 1000
      val uuid = IzUUID.getTimeUUIDFromMicros(nowMicros)
      assert(uuid != null)
      assert(uuid.version() == 1)
    }

    "create random time UUID from microseconds" in {
      val nowMicros = System.currentTimeMillis() * 1000
      val uuid1 = IzUUID.getRandomTimeUUIDFromMicros(nowMicros)
      val uuid2 = IzUUID.getRandomTimeUUIDFromMicros(nowMicros)
      assert(uuid1 != null)
      assert(uuid2 != null)
      assert(uuid1 != uuid2)
    }

    "create time UUID with nanos" in {
      val now = System.currentTimeMillis()
      val uuid = IzUUID.getTimeUUID(now, 5000L)
      assert(uuid != null)
      assert(uuid.version() == 1)
    }

    "create time UUID with nanos and clockSeqAndNode" in {
      val now = System.currentTimeMillis()
      val uuid = IzUUID.getTimeUUID(now, 5000L, 0x123456789ABCL)
      assert(uuid != null)
      assert(uuid.version() == 1)
    }

    "parse UUID from ByteBuffer" in {
      val original = IzUUID.generateTimeUUID()
      val bytes = IzUUID.decompose(original)
      val buffer = ByteBuffer.wrap(bytes)
      val parsed = IzUUID.getUUID(buffer)
      assert(parsed == original)
    }

    "decompose UUID to bytes" in {
      val uuid = IzUUID.generateTimeUUID()
      val bytes = IzUUID.decompose(uuid)
      assert(bytes.length == 16)
    }

    "generate minTimeUUID" in {
      val now = System.currentTimeMillis()
      val minUuid = IzUUID.minTimeUUID(now)
      assert(minUuid != null)
      assert(minUuid.version() == 1)
    }

    "generate maxTimeUUID" in {
      val now = System.currentTimeMillis()
      val maxUuid = IzUUID.maxTimeUUID(now)
      assert(maxUuid != null)
      assert(maxUuid.version() == 1)
    }

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

    "get time UUID bytes from millis" in {
      val now = System.currentTimeMillis()
      val bytes = IzUUID.getTimeUUIDBytes(now)
      assert(bytes.length == 16)
    }

    "get time UUID bytes from millis and nanos" in {
      val now = System.currentTimeMillis()
      val bytes = IzUUID.getTimeUUIDBytes(now, 5000)
      assert(bytes.length == 16)
    }

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

}
