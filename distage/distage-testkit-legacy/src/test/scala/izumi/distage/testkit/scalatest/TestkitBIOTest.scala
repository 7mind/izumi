package izumi.distage.testkit.scalatest

import distage.{ModuleBase, TagKK}
import izumi.distage.model.definition.ModuleDef
import izumi.distage.testkit.services.scalatest.adapter.DISyntaxBIO
import izumi.functional.bio.{BIO, BIOError, F}
import scalatest.adapter.specs.DistagePluginBioSpec

@deprecated("Use dstest", "2019/Jul/18")
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

@deprecated("Use dstest", "2019/Jul/18")
class TestkitBIOTestZio extends TestkitBIOTest[zio.IO]
