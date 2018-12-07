package com.github.pshirshov.izumi.idealingua.il.loader

import java.io.File
import java.nio.file.Path


class LocalModelLoaderContext(src: Path, cp: Seq[File]) extends ModelLoaderContextImpl(LocalModelLoaderContext.makeEnumerator(src, cp))

object LocalModelLoaderContext {
  def makeEnumerator(src: Path, cp: Seq[File])(c: BaseModelLoadContext): LocalFilesystemEnumerator = new LocalFilesystemEnumerator(src, cp, Set(c.modelExt, c.domainExt))
}
