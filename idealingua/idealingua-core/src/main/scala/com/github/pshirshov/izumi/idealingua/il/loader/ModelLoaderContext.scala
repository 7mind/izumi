package com.github.pshirshov.izumi.idealingua.il.loader

trait ModelLoaderContext {
  def domainExt: String
  def modelExt: String

  def parser: ModelParser
  def enumerator: FilesystemEnumerator
  def resolver: ModelResolver
  def loader: ModelLoader
}
