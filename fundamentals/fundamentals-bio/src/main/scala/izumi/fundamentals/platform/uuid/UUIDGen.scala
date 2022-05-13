/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package izumi.fundamentals.platform.uuid

import java.net.{InetAddress, NetworkInterface}
import java.nio.ByteBuffer
import java.security.{MessageDigest, SecureRandom}
import java.util.{Collection, Collections, Enumeration, HashSet, Random, Set, UUID}

import izumi.fundamentals.platform.uuid.UUIDGen._

object UUIDGen {

  // A grand day! millis at 00:00:00.000 15 Oct 1582.
  private val START_EPOCH: Long = -12219292800000L

  private val clockSeqAndNode: Long = makeClockSeqAndNode()

  /*
   * The min and max possible lsb for a UUID.
   * Note that his is not 0 and all 1's because Cassandra TimeUUIDType
   * compares the lsb parts as a signed byte array comparison. So the min
   * value is 8 times -128 and the max is 8 times +127.
   *
   * Note that we ignore the uuid variant (namely, MIN_CLOCK_SEQ_AND_NODE
   * have variant 2 as it should, but MAX_CLOCK_SEQ_AND_NODE have variant 0).
   * I don't think that has any practical consequence and is more robust in
   * case someone provides a UUID with a broken variant.
   */

  private val MIN_CLOCK_SEQ_AND_NODE: Long = 0x8080808080808080L

  private val MAX_CLOCK_SEQ_AND_NODE: Long = 0x7F7F7F7F7F7F7F7FL

  private val secureRandom: SecureRandom = new SecureRandom

  // placement of this singleton is important.  It needs to be instantiated *AFTER* the other statics.
  private val instance: UUIDGen = new UUIDGen

  /**
    * Creates a type 1 UUID (time-based UUID).
    *
    * @return a UUID instance
    */
  def getTimeUUID(): UUID =
    new UUID(instance.createTimeSafe(), clockSeqAndNode)

  /**
    * Creates a type 1 UUID (time-based UUID) with the timestamp of @param when, in milliseconds.
    *
    * @return a UUID instance
    */
  def getTimeUUID(when: Long): UUID =
    new UUID(createTime(fromUnixTimestamp(when)), clockSeqAndNode)

  /**
    * Returns a version 1 UUID using the provided timestamp and the local clock and sequence.
    * <p>
    * Note that this method is generally only safe to use if you can guarantee that the provided
    * parameter is unique across calls (otherwise the returned UUID won't be unique accross calls).
    *
    * @param whenInMicros a unix time in microseconds.
    * @return a new UUID { @code id} such that { @code microsTimestamp(id) == whenInMicros}. Please not that
    *                            multiple calls to this method with the same value of { @code whenInMicros} will return the <b>same</b>
    *                            UUID.
    */
  def getTimeUUIDFromMicros(whenInMicros: Long): UUID = {
    val whenInMillis: Long = whenInMicros / 1000
    val nanos: Long = (whenInMicros - whenInMillis * 1000) * 10
    getTimeUUID(whenInMillis, nanos)
  }

  /**
    * Similar to {@link getTimeUUIDFromMicros}, but randomize (using SecureRandom) the clock and sequence.
    * <p>
    * If you can guarantee that the {@code whenInMicros} argument is unique (for this JVM instance) for
    * every call, then you should prefer {@link getTimeUUIDFromMicros} which is faster. If you can't
    * guarantee this however, this method will ensure the returned UUID are still unique (accross calls)
    * through randomization.
    *
    * @param whenInMicros a unix time in microseconds.
    * @return a new UUID { @code id} such that { @code microsTimestamp(id) == whenInMicros}. The UUID returned
    *                            by different calls will be unique even if { @code whenInMicros} is not.
    */
  def getRandomTimeUUIDFromMicros(whenInMicros: Long): UUID = {
    val whenInMillis: Long = whenInMicros / 1000
    val nanos: Long = (whenInMicros - whenInMillis * 1000) * 10
    new UUID(createTime(fromUnixTimestamp(whenInMillis, nanos)), secureRandom.nextLong())
  }

  def getTimeUUID(when: Long, nanos: Long): UUID =
    new UUID(createTime(fromUnixTimestamp(when, nanos)), clockSeqAndNode)

  def getTimeUUID(when: Long, nanos: Long, clockSeqAndNode: Long): UUID =
    new UUID(createTime(fromUnixTimestamp(when, nanos)), clockSeqAndNode)

  /**
    * creates a type 1 uuid from raw bytes.
    */
  def getUUID(raw: ByteBuffer): UUID =
    new UUID(raw.getLong(raw.position()), raw.getLong(raw.position() + 8))

  /**
    * decomposes a uuid into raw bytes.
    */
  def decompose(uuid: UUID): Array[Byte] = {
    val most: Long = uuid.getMostSignificantBits
    val least: Long = uuid.getLeastSignificantBits
    val b: Array[Byte] = Array.ofDim[Byte](16)
    for (i <- 0.until(8)) {
      b(i) = (most >>> (7 - i) * 8).toByte
      b(8 + i) = (least >>> (7 - i) * 8).toByte
    }
    b
  }

  /**
    * Returns a 16 byte representation of a type 1 UUID (a time-based UUID),
    * based on the current system time.
    *
    * @return a type 1 UUID represented as a byte[]
    */
  def getTimeUUIDBytes(): Array[Byte] =
    createTimeUUIDBytes(instance.createTimeSafe())

  /**
    * Returns the smaller possible type 1 UUID having the provided timestamp.
    *
    * <b>Warning:</b> this method should only be used for querying as this
    * doesn't at all guarantee the uniqueness of the resulting UUID.
    */
  def minTimeUUID(timestamp: Long): UUID =
    new UUID(createTime(fromUnixTimestamp(timestamp)), MIN_CLOCK_SEQ_AND_NODE)

  /**
    * Returns the biggest possible type 1 UUID having the provided timestamp.
    *
    * <b>Warning:</b> this method should only be used for querying as this
    * doesn't at all guarantee the uniqueness of the resulting UUID.
    */
  def maxTimeUUID(timestamp: Long): UUID = {
    // precision by taking 10000, but rather 19999.
    val uuidTstamp: Long = fromUnixTimestamp(timestamp + 1) - 1
    new UUID(createTime(uuidTstamp), MAX_CLOCK_SEQ_AND_NODE)
  }

  // unix timestamp are milliseconds precision, uuid timestamp are 100's
  // nanoseconds precision. If we ask for the biggest uuid have unix
  // timestamp 1ms, then we should not extend 100's nanoseconds
  // unix timestamp are milliseconds precision, uuid timestamp are 100's
  // nanoseconds precision. If we ask for the biggest uuid have unix
  // timestamp 1ms, then we should not extend 100's nanoseconds

  /**
    * @param uuid
    * @return milliseconds since Unix epoch
    */
  def unixTimestamp(uuid: UUID): Long =
    uuid.timestamp() / 10000 + START_EPOCH

  /**
    * @param uuid
    * @return microseconds since Unix epoch
    */
  def microsTimestamp(uuid: UUID): Long =
    uuid.timestamp() / 10 + START_EPOCH * 1000

  /**
    * @param timestamp milliseconds since Unix epoch
    * @return
    */
  private def fromUnixTimestamp(timestamp: Long): Long =
    fromUnixTimestamp(timestamp, 0L)

  private def fromUnixTimestamp(timestamp: Long, nanos: Long): Long =
    (timestamp - START_EPOCH) * 10000 + nanos

  /**
    * Converts a milliseconds-since-epoch timestamp into the 16 byte representation
    * of a type 1 UUID (a time-based UUID).
    *
    * <p><i><b>Deprecated:</b> This method goes again the principle of a time
    * UUID and should not be used. For queries based on timestamp, minTimeUUID() and
    * maxTimeUUID() can be used but this method has questionable usefulness. This is
    * only kept because CQL2 uses it (see TimeUUID.fromStringCQL2) and we
    * don't want to break compatibility.</i></p>
    *
    * <p><i><b>Warning:</b> This method is not guaranteed to return unique UUIDs; Multiple
    * invocations using identical timestamps will result in identical UUIDs.</i></p>
    *
    * @param timeMillis
    * @return a type 1 UUID represented as a byte[]
    */
  def getTimeUUIDBytes(timeMillis: Long): Array[Byte] =
    createTimeUUIDBytes(instance.createTimeUnsafe(timeMillis))

  /**
    * Converts a 100-nanoseconds precision timestamp into the 16 byte representation
    * of a type 1 UUID (a time-based UUID).
    *
    * To specify a 100-nanoseconds precision timestamp, one should provide a milliseconds timestamp and
    * a number 0 <= n < 10000 such that n*100 is the number of nanoseconds within that millisecond.
    *
    * <p><i><b>Warning:</b> This method is not guaranteed to return unique UUIDs; Multiple
    * invocations using identical timestamps will result in identical UUIDs.</i></p>
    *
    * @return a type 1 UUID represented as a byte[]
    */
  def getTimeUUIDBytes(timeMillis: Long, nanos: Int): Array[Byte] = {
    if (nanos >= 10000) throw new IllegalArgumentException
    createTimeUUIDBytes(instance.createTimeUnsafe(timeMillis, nanos))
  }

  private def createTimeUUIDBytes(msb: Long): Array[Byte] = {
    val lsb: Long = clockSeqAndNode
    val uuidBytes: Array[Byte] = Array.ofDim[Byte](16)
    for (i <- 0.until(8)) uuidBytes(i) = (msb >>> 8 * (7 - i)).toByte
    for (i <- 8.until(16)) uuidBytes(i) = (lsb >>> 8 * (7 - i)).toByte
    uuidBytes
  }

  /**
    * Returns a milliseconds-since-epoch value for a type-1 UUID.
    *
    * @param uuid a type-1 (time-based) UUID
    * @return the number of milliseconds since the unix epoch
    * @throws IllegalArgumentException if the UUID is not version 1
    */
  def getAdjustedTimestamp(uuid: UUID): Long = {
    if (uuid.version() != 1)
      throw new IllegalArgumentException("incompatible with uuid version: " + uuid.version())
    uuid.timestamp() / 10000 + START_EPOCH
  }

  private def makeClockSeqAndNode(): Long = {
    val clock: Long = new Random(System.currentTimeMillis()).nextLong()
    var lsb: Long = 0
    // variant (2 bits)
    lsb |= 0x8000000000000000L
    // clock sequence (14 bits)
    lsb |= (clock & 0x0000000000003FFFL) << 48
    // 6 bytes
    lsb |= makeNode()
    lsb
  }

  private def createTime(nanosSince: Long): Long = {
    var msb: Long = 0L
    msb |= (0x00000000FFFFFFFFL & nanosSince) << 32
    msb |= (0x0000FFFF00000000L & nanosSince) >>> 16
    msb |= (0xFFFF000000000000L & nanosSince) >>> 48
    // sets the version to 1.
    msb |= 0x0000000000001000L
    msb
  }

  def getAllLocalAddresses(): Collection[InetAddress] = {
    val localAddresses: Set[InetAddress] = new HashSet[InetAddress]
    val nets: Enumeration[NetworkInterface] =
      NetworkInterface.getNetworkInterfaces
    if (nets != null) {
      while (nets.hasMoreElements()) localAddresses.addAll(Collections.list(nets.nextElement().getInetAddresses))
    }
    localAddresses
  }

  private def makeNode(): Long = {
    /*
     * We don't have access to the MAC address but need to generate a node part
     * that identify this host as uniquely as possible.
     * The spec says that one option is to take as many source that identify
     * this node as possible and hash them together. That's what we do here by
     * gathering all the ip of this host.
     * Note that FBUtilities.getBroadcastAddress() should be enough to uniquely
     * identify the node *in the cluster* but it triggers DatabaseDescriptor
     * instanciation and the UUID generator is used in Stress for instance,
     * where we don't want to require the yaml.
     */

    val localAddresses: Collection[InetAddress] = getAllLocalAddresses()
    if (localAddresses.isEmpty)
      throw new RuntimeException("Cannot generate the node component of the UUID because cannot retrieve any IP addresses.")
    // ideally, we'd use the MAC address, but java doesn't expose that.
    val hash: Array[Byte] = doHash(localAddresses)
    var node: Long = 0
    for (i <- 0 until Math.min(6, hash.length))
      node |= (0x00000000000000FF & hash(i).toLong) << (5 - i) * 8
    assert((0xFF00000000000000L & node) == 0)
    // bit (least significant bit of the first octet of the node ID) must be 1.
    node | 0x0000010000000000L
  }

  // Since we don't use the mac address, the spec says that multicast
  // Since we don't use the mac address, the spec says that multicast

  private def doHash(data: Collection[InetAddress]): Array[Byte] = {
    import scala.jdk.CollectionConverters._
    val messageDigest: MessageDigest = MessageDigest.getInstance("MD5")
    for (addr <- data.asScala) messageDigest.update(addr.getAddress)
    messageDigest.digest()
  }

}

/**
  * The goods are here: www.ietf.org/rfc/rfc4122.txt.
  */
class UUIDGen protected {

  private var lastNanos: Long = _

  // make sure someone didn't whack the clockSeqAndNode by changing the order of instantiation.
  if (clockSeqAndNode == 0)
    throw new RuntimeException("singleton instantiation is misplaced.")

  // we can generate at most 10k UUIDs per ms.
  private def createTimeSafe(): Long = synchronized {
    var nanosSince: Long = (System.currentTimeMillis() - START_EPOCH) * 10000
    if (nanosSince > lastNanos) {
      lastNanos = nanosSince
    } else {
      lastNanos += 1
      nanosSince = lastNanos
    }
    createTime(nanosSince)
  }

  /**
    * @param when time in milliseconds
    */
  private def createTimeUnsafe(when: Long): Long = createTimeUnsafe(when, 0)

  private def createTimeUnsafe(when: Long, nanos: Int): Long = {
    val nanosSince: Long = (when - START_EPOCH) * 10000 + nanos
    createTime(nanosSince)
  }

}

// for the curious, here is how I generated START_EPOCH
//        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT-0"));
//        c.set(Calendar.YEAR, 1582);
//        c.set(Calendar.MONTH, Calendar.OCTOBER);
//        c.set(Calendar.DAY_OF_MONTH, 15);
//        c.set(Calendar.HOUR_OF_DAY, 0);
//        c.set(Calendar.MINUTE, 0);
//        c.set(Calendar.SECOND, 0);
//        c.set(Calendar.MILLISECOND, 0);
//        long START_EPOCH = c.getTimeInMillis();
