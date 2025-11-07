package izumi.fundamentals.platform.uuid

import java.nio.ByteBuffer
import java.util.UUID

// Simplified JS implementation using java.util.UUID.randomUUID()
// scalajs-javalib provides java.util.UUID but not MessageDigest, InetAddress, NetworkInterface
trait IzUUIDImpl extends IzUUID {

  override def generateTimeUUID(): UUID = {
    // On JS, just use random UUIDs since we don't have access to MAC address, MessageDigest, etc.
    // This is good enough for test IDs
    UUID.randomUUID()
  }

  override def generateTimeUUIDBytes(): Array[Byte] = {
    decompose(generateTimeUUID())
  }

  override def getTimeUUID(when: Long): UUID = {
    // Simplified: create a time-based UUID using timestamp
    // Use version 1 UUID format but with random node
    createTimeUUID(when, (math.random() * Long.MaxValue).toLong)
  }

  override def getTimeUUIDFromMicros(whenInMicros: Long): UUID = {
    getTimeUUID(whenInMicros / 1000)
  }

  override def getRandomTimeUUIDFromMicros(whenInMicros: Long): UUID = {
    getTimeUUID(whenInMicros / 1000)
  }

  override def getTimeUUID(when: Long, nanos: Long): UUID = {
    getTimeUUID(when)
  }

  override def getTimeUUID(when: Long, nanos: Long, clockSeqAndNode: Long): UUID = {
    createTimeUUID(when, clockSeqAndNode)
  }

  override def getUUID(raw: ByteBuffer): UUID = {
    val msb = raw.getLong
    val lsb = raw.getLong
    new UUID(msb, lsb)
  }

  override def decompose(uuid: UUID): Array[Byte] = {
    val bb = ByteBuffer.wrap(new Array[Byte](16))
    bb.putLong(uuid.getMostSignificantBits)
    bb.putLong(uuid.getLeastSignificantBits)
    bb.array()
  }

  override def minTimeUUID(timestamp: Long): UUID = {
    new UUID(createTime(timestamp), Long.MinValue)
  }

  override def maxTimeUUID(timestamp: Long): UUID = {
    new UUID(createTime(timestamp), Long.MaxValue)
  }

  override def unixTimestamp(uuid: UUID): Long = {
    microsTimestamp(uuid) / 1000
  }

  override def microsTimestamp(uuid: UUID): Long = {
    val timestamp = uuid.getMostSignificantBits
    (timestamp & 0x0FFFL) << 48 | ((timestamp >> 16) & 0x0FFFFFFFFL)
  }

  override def getTimeUUIDBytes(timeMillis: Long): Array[Byte] = {
    decompose(getTimeUUID(timeMillis))
  }

  override def getTimeUUIDBytes(timeMillis: Long, nanos: Int): Array[Byte] = {
    decompose(getTimeUUID(timeMillis, nanos.toLong))
  }

  override def getAdjustedTimestamp(uuid: UUID): Long = {
    (microsTimestamp(uuid) - numHundredsFrom15821582) / 10
  }

  private def createTimeUUID(timestamp: Long, clockSeqAndNode: Long): UUID = {
    new UUID(createTime(timestamp), clockSeqAndNode)
  }

  private def createTime(timestamp: Long): Long = {
    var msb = 0L
    val uuidTimestamp = timestamp * 10000 + numHundredsFrom15821582
    msb |= (0x00000000FFFFFFFFL & uuidTimestamp) << 32
    msb |= (0x0000FFFF00000000L & uuidTimestamp) >>> 16
    msb |= (0x0FFF000000000000L & uuidTimestamp) >>> 48
    msb |= 0x0000000000001000L // version 1
    msb
  }

  private val numHundredsFrom15821582 = 0x01b21dd213814000L
}
