package izumi.thirdparty.internal.boopickle

object DefaultByteBufferProvider extends DefaultByteBufferProviderFuncs {
  override def provider = new HeapByteBufferProvider
}
