/*
 * Adapted from Cats Effect v3.7.1 std/native/src/main/scala/cats/effect/std/SecureRandomCompanionPlatform.scala
 * Copyright 2020-2025 Typelevel, Licensed under the Apache License, Version 2.0.
 */
package izumi.fundamentals.platform.uuid

import scala.scalanative.libc.errno.*
import scala.scalanative.libc.string.*
import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

/** Native variant: `java.security.SecureRandom` does not exist on Scala Native, so delegate to `getentropy`. */
private[uuid] object __SecureRandomPlatformSpecific {

  private final val MaximumEntropyRequestBytes = 256

  private[uuid] class SecureRandomImpl() extends java.util.Random(0L) {

    override def setSeed(x: Long): Unit = ()

    override def nextBytes(bytes: Array[Byte]): Unit = {
      val len = bytes.length
      var i = 0
      while (i < len) {
        val n = Math.min(MaximumEntropyRequestBytes, len - i)
        if (sysrandom.getentropy(bytes.atUnsafe(i), n.toCSize) < 0)
          throw new RuntimeException(fromCString(strerror(errno)))
        i += n
      }
    }

    override protected final def next(numBits: Int): Int = {
      if (numBits <= 0) {
        0
      } else {
        val bytes = stackalloc[CInt]()
        if (sysrandom.getentropy(bytes.asInstanceOf[Ptr[Byte]], sizeof[CInt]) < 0)
          throw new RuntimeException(fromCString(strerror(errno)))
        val rand32: Int = !bytes
        rand32 & (-1 >>> (32 - numBits))
      }
    }
  }

  @extern
  private object sysrandom {
    def getentropy(buf: Ptr[Byte], buflen: CSize): Int = extern
  }
}
