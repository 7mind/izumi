package izumi.fundamentals.platform.uuid

import java.net.{InetAddress, NetworkInterface}
import java.security.MessageDigest
import java.util.{Collections, UUID}

trait IzUUIDPlatformSpecific {

  @inline final def generateRandomUUID(): UUID = UUID.randomUUID()

  /**
    * Generate the 48-bit node ID for Time UUIDs.
    *
    * On JVM, we hash all local network addresses to create a node ID
    * that is unique to this host. Per RFC 4122, since we're not using
    * a real MAC address, we set the multicast bit (bit 0 of the first octet)
    * to indicate this is a randomly/hash-generated node ID.
    */
  protected def makeNode(): Long = {
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

    val localAddresses: java.util.Collection[InetAddress] = getAllLocalAddresses()
    if (localAddresses.isEmpty)
      throw new RuntimeException("Cannot generate the node component of the UUID because cannot retrieve any IP addresses.")
    // ideally, we'd use the MAC address, but java doesn't expose that.
    val hash: Array[Byte] = doHash(localAddresses)
    var node: Long = 0
    for (i <- 0 until Math.min(6, hash.length))
      node |= (0x00000000000000FF & hash(i).toLong) << (5 - i) * 8
    assert((0xFF00000000000000L & node) == 0)
    // Since we don't use the mac address, the spec says that multicast
    // bit (least significant bit of the first octet of the node ID) must be 1.
    node | 0x0000010000000000L
  }

  private def getAllLocalAddresses(): java.util.Collection[InetAddress] = {
    val localAddresses = new java.util.HashSet[InetAddress]()
    val nets = NetworkInterface.getNetworkInterfaces
    while (nets.hasMoreElements) localAddresses.addAll(Collections.list(nets.nextElement().getInetAddresses))
    localAddresses
  }

  private def doHash(data: java.util.Collection[InetAddress]): Array[Byte] = {
    import scala.jdk.CollectionConverters.*
    val messageDigest: MessageDigest = MessageDigest.getInstance("MD5")
    for (addr <- data.asScala) messageDigest.update(addr.getAddress)
    messageDigest.digest()
  }

}
