package izumi.fundamentals.platform.uuid

import java.util.UUID

trait IzUUIDPlatformSpecific {

  protected def secureRandom: java.util.Random

  /** same as [[java.util.UUID.randomUUID]], but using JS-specific [[__SecureRandomPlatformSpecific.SecureRandomImpl]] */
  final def generateRandomUUID(): UUID = {
    val ng = secureRandom

    val randomBytes = new Array[Byte](16)
    ng.nextBytes(randomBytes)
    randomBytes(6) = (randomBytes(6) & (0x0F: Byte)).toByte /* clear version        */
    randomBytes(6) = (randomBytes(6) | (0x40: Byte)).toByte /* set to version 4     */
    randomBytes(8) = (randomBytes(8) & (0x3F: Byte)).toByte /* clear variant        */
    randomBytes(8) = (randomBytes(8) | 0x80.toByte).toByte /* set to IETF variant  */

    var msb: Long = 0L
    var lsb: Long = 0L
    locally {
      var i = 0
      while (i < 8) {
        msb = (msb << 8) | (randomBytes(i) & 0xFF)
        i += 1
      }
    }
    locally {
      var i = 8
      while (i < 16) {
        lsb = (lsb << 8) | (randomBytes(i) & 0xFF)
        i += 1
      }
    }
    val mostSigBits = msb
    val leastSigBits = lsb
    new UUID(mostSigBits, leastSigBits)
  }

  /**
    * Generate the 48-bit node ID for Time UUIDs.
    *
    * On JavaScript platforms, we cannot access network interfaces or MAC addresses.
    * Per RFC 4122 Section 4.5, when a MAC address is not available, we generate
    * a random 47-bit value and set the multicast bit (bit 0 of the first octet)
    * to indicate this is not a real IEEE 802 address.
    *
    * The node ID is generated once and cached, following the same approach
    * as the widely-used `uuid` npm package.
    */
  protected def makeNode(): Long = {
    // Generate 48 bits (6 bytes) of random data for the node ID
    var node: Long = 0L

    // Generate random bytes for each of the 6 byte positions
    for (i <- 0 until 6) {
      val randomByte = (secureRandom.nextInt(256) & 0xFF).toLong
      node |= randomByte << ((5 - i) * 8)
    }

    // Ensure the high byte is clear (node is only 48 bits)
    assert((0xFF00000000000000L & node) == 0)

    // Per RFC 4122: Set the multicast bit (bit 0 of the first octet) to 1
    // to indicate this is not a real MAC address.
    // The first octet is in bits 40-47 (the high byte of the 48-bit node).
    node | 0x0000010000000000L
  }

}
