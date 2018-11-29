package com.github.pshirshov.izumi.idealingua.il.loader

abstract class ModelLoaderContextImpl extends ModelLoaderContext {
  val domainExt: String = ".domain"

  val modelExt: String = ".model"

  val parser = new ModelParserImpl()

  lazy val loader = new ModelLoaderImpl(enumerator, parser, resolver, modelExt, domainExt)

  lazy val resolver = new ModelResolverImpl(domainExt)
}
