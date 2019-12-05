package izumi.distage.testkit.st

import distage.{ModuleBase, TagK, TagKK}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.testkit.services.st.adapter.DISyntaxBIO
import izumi.distage.testkit.st.adapter.specs.DistagePluginBioSpec
import izumi.functional.bio.{BIO, BIOError, F}

abstract class TestkitBIOTest[F[+_, +_]: BIO: TagKK] extends DistagePluginBioSpec[F]
  with DISyntaxBIO[F] {

  override protected def pluginPackages: Seq[String] = thisPackage

  override protected def appOverride: ModuleBase = super.appOverride ++ new ModuleDef {
    addImplicit[BIOError[F]]
    addImplicit[BIO[F]]
  }

  "bio test with `Any` error" in dio {
    () =>
      F.when(false) {
        F.fail("string failure!")
      } *> BIO(println("success!"))
  }

}

class TestkitBIOTestZio extends TestkitBIOTest[zio.IO]
