package izumi.reflect.lttrenderables

object _PrivateIzumiReflect {
  // FIXME: un-private LTTRenderables in next release of izumi-reflect
  type LTTRenderables = izumi.reflect.macrortti.LTTRenderables
  // FIXME: un-private RuntimeAPI in next release of izumi-reflect
  @inline final def RuntimeAPI: izumi.reflect.macrortti.RuntimeAPI.type = izumi.reflect.macrortti.RuntimeAPI
}
