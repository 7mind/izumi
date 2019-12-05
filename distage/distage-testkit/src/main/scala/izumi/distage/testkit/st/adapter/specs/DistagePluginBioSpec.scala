package izumi.distage.testkit.st.adapter.specs

import distage.TagKK

@deprecated("Use dstest", "2019/Jul/18")
abstract class DistagePluginBioSpec[F[_, _]](implicit val tagBIO: TagKK[F]) extends DistagePluginSpec[F[Throwable, ?]]()
