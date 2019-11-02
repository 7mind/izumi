package izumi.idealingua.il.loader

import java.io.File
import java.nio.file.Path


class LocalModelLoaderContext(src: Seq[Path], cp: Seq[File]) extends ModelLoaderContextImpl(LocalModelLoaderContext.makeEnumerator(src, cp))

object LocalModelLoaderContext {
  def makeEnumerator(src: Seq[Path], cp: Seq[File])(c: BaseModelLoadContext): LocalFilesystemEnumerator = {
    val allExts = Set(c.modelExt, c.domainExt, c.overlayExt)
    new LocalFilesystemEnumerator(src, cp, allExts)
  }
}
