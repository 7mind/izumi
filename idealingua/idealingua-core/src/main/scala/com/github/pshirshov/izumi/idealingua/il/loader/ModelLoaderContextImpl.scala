package com.github.pshirshov.izumi.idealingua.il.loader

class ModelLoaderContextImpl(makeEnumerator: BaseModelLoadContext => FilesystemEnumerator) extends ModelLoaderContext {
  val domainExt: String = ".domain"

  val modelExt: String = ".model"

  val parser = new ModelParserImpl()

  val enumerator: FilesystemEnumerator = makeEnumerator(this)

  val loader = new ModelLoaderImpl(enumerator, parser, modelExt, domainExt)
}
