package izumi.idealingua

import java.nio.file.Paths

import izumi.fundamentals.platform.jvm.IzJvm
import izumi.idealingua.il.loader.{FilesystemEnumerator, LocalFilesystemEnumerator, ModelLoaderImpl}
import izumi.idealingua.il.renderer.{IDLRenderer, IDLRenderingOptions}
import org.scalatest.wordspec.AnyWordSpec


class LoaderTest extends AnyWordSpec {

  "IL loader" should {
    "parse all test domains" in {
      testIn("/defs/any")
      testIn("/defs/scala")
    }
  }

  "FS enumerator" should {
    "be able to find files in jars" in {
      val classpath = IzJvm.safeClasspath().split(':')
      val enumerator = new LocalFilesystemEnumerator(Seq(Paths.get("/tmp/nonexistent")), classpath.filter(_.contains("fastparse")).map(p => Paths.get(p).toFile), Set(".MF"))
      assert(enumerator.enumerate().size == 1)
    }
  }


  private def testIn(base: String): Unit = {
    val loader = IDLTestTools.makeLoader(base)
    val resolver = IDLTestTools.makeResolver(base)
    val defs = IDLTestTools.loadDefs(loader, resolver)

    assert(defs.nonEmpty)

    defs.foreach {
      original =>
        val domainId = original.typespace.domain.id
        assert(original.typespace.domain.nonEmpty, s"$domainId parsed into empty definition (location: ${original.path})")
    }

    val files = loader.enumerator.enumerate()

    defs.foreach {
      original =>
        val ts = original.typespace
        val domainId = ts.domain.id
        val rendered = new IDLRenderer(ts.domain, IDLRenderingOptions(expandIncludes = false)).render()
        val updated = files.updated(original.path, rendered)

        val enumerator = new FilesystemEnumerator.Pseudo(updated.map {
          case (k, v) => k.asString -> v
        })
        val unresolved = new ModelLoaderImpl(enumerator, loader.parser, loader.modelExt, loader.domainExt, loader.overlayExt).load()

        val typespaces = resolver.resolve(unresolved).throwIfFailed().successful
        val restoredTypespace = typespaces.find(_.typespace.domain.id == domainId).get.typespace

        // TODO: custom equality check ignoring meta
        assert(restoredTypespace.domain.types.toSet == ts.domain.types.toSet, domainId)
        assert(restoredTypespace.domain.services.toSet == ts.domain.services.toSet, domainId)
        assert(restoredTypespace.domain.buzzers.toSet == ts.domain.buzzers.toSet, domainId)
        assert(restoredTypespace.domain.streams.toSet == ts.domain.streams.toSet, domainId)
    }
  }


}

