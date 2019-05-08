package com.github.pshirshov.izumi.distage.testkit

import com.github.pshirshov.izumi.distage.model.definition.ModuleDef
import com.github.pshirshov.izumi.functional.bio.{BIO, BIOError}
import com.github.pshirshov.izumi.functional.bio.BIO.syntax._
import distage.{ModuleBase, TagK, TagKK}

abstract class TestkitBIOTest[F[+_, +_]: BIO: TagKK: Lambda[f[_, _] => TagK[f[Throwable, ?]]]] extends DistagePluginBioSpec[F]
  with DistageBioSpecBIOSyntax[F] {

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

class TestkitBIOTestZio extends TestkitBIOTest[scalaz.zio.IO]
