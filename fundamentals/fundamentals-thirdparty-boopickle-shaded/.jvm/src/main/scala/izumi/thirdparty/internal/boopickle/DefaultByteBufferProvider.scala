package izumi.thirdparty.internal.boopickle

private[izumi] object DefaultByteBufferProvider extends DefaultByteBufferProviderFuncs {
  override def provider = new HeapByteBufferProvider
}
