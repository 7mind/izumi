package izumi.fundamentals

import izumi.functional.IzEither
import izumi.fundamentals.collections.IzCollections
import izumi.fundamentals.platform.basics.IzBoolean
import izumi.fundamentals.platform.bytes.IzBytes
import izumi.fundamentals.platform.exceptions.IzThrowable
import izumi.fundamentals.platform.serialization.IzSerializable
import izumi.fundamentals.platform.strings.IzString
import izumi.fundamentals.platform.time.{IzTimeOrderingSafe, IzTimeSafe}

object preamble extends IzBoolean with IzBytes with IzCollections with IzEither with IzSerializable with IzString with IzThrowable with IzTimeOrderingSafe {}
