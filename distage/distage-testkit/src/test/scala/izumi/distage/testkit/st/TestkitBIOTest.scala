package izumi.distage.testkit.st

import izumi.distage.model.definition.ModuleDef
import izumi.distage.testkit.services.st.adapter.DISyntaxBIO
import izumi.distage.testkit.st.adapter.specs.DistagePluginBioSpec
import izumi.functional.bio.{BIO, BIOError}
import distage.{ModuleBase, TagK, TagKK}

abstract class TestkitBIOTest[F[+_, +_]: BIO: TagKK](implicit ev: TagK[F[Throwable, ?]]) extends DistagePluginBioSpec[F]
  with DISyntaxBIO[F] {

  override protected def pluginPackages: Seq[String] = thisPackage

  override protected def appOverride: ModuleBase = super.appOverride ++ new ModuleDef {
    addImplicit[BIOError[F]]
    addImplicit[BIO[F]]
  }

  "bio test with `Any` error" in dio {
    () =>
      BIO[F].when(false) {
        BIO[F].fail("string failure!")
      } *> BIO(println("success!"))
  }

}

class TestkitBIOTestZio extends TestkitBIOTest[zio.IO]
