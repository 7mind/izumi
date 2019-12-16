package izumi.distage.testkit

import distage.TagKK

@deprecated("Use dstest", "2019/Jul/18")
abstract class DistageBioSpec[F[_, _]](implicit val tagBIO: TagKK[F]) extends DistageSpec[F[Throwable, ?]]
