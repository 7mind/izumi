package izumi.fundamentals.platform.uuid

import izumi.fundamentals.platform.IzPlatformEffectfulUtil

import java.nio.ByteBuffer
import java.util.UUID

trait IzUUID extends IzPlatformEffectfulUtil {
  final val Zero: UUID = new UUID(0L, 0L)

  def generateTimeUUID(): UUID
  def generateTimeUUIDBytes(): Array[Byte]

  def getTimeUUID(when: Long): UUID
  def getTimeUUIDFromMicros(whenInMicros: Long): UUID
  def getRandomTimeUUIDFromMicros(whenInMicros: Long): UUID
  def getTimeUUID(when: Long, nanos: Long): UUID
  def getTimeUUID(when: Long, nanos: Long, clockSeqAndNode: Long): UUID
  def getUUID(raw: ByteBuffer): UUID
  def decompose(uuid: UUID): Array[Byte]
  def minTimeUUID(timestamp: Long): UUID
  def maxTimeUUID(timestamp: Long): UUID
  def unixTimestamp(uuid: UUID): Long
  def microsTimestamp(uuid: UUID): Long
  def getTimeUUIDBytes(timeMillis: Long): Array[Byte]
  def getTimeUUIDBytes(timeMillis: Long, nanos: Int): Array[Byte]
  def getAdjustedTimestamp(uuid: UUID): Long
}

object IzUUID extends IzUUID with IzUUIDImpl {}
