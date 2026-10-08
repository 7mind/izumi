package izumi.distage.provisioning.strategies

private[strategies] trait UnsupportedProxyPlatformSpecific {
  protected final def cogenProxyFailureMessage: String = "cglib proxies are not supported on Scala.js, check documentation & try using by-name parameters!"
}
