package izumi.idealingua.il.loader

protected[loader] trait BaseModelLoadContext {
  def domainExt: String
  def modelExt: String
  def overlayExt: String

  def parser: ModelParser
  def loader: ModelLoader
}

trait ModelLoaderContext extends BaseModelLoadContext {

  def enumerator: FilesystemEnumerator
}
