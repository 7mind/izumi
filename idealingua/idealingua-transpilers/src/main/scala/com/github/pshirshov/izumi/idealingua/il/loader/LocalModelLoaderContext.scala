package com.github.pshirshov.izumi.idealingua.il.loader

import java.io.File
import java.nio.file.Path





class LocalModelLoaderContext(src: Path, cp: Seq[File]) extends ModelLoaderContextImpl {
  val enumerator = new LocalFilesystemEnumerator(src, Set(modelExt, domainExt))
  val resolver = new LocalModelResolver(src, cp, parser, domainExt)
}
