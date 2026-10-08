package izumi.distage.provisioning.strategies

private[strategies] trait UnsupportedProxyPlatformSpecific {
  protected final def cogenProxyFailureMessage: String = "Generated proxies are not supported on Scala Native, check documentation & try using by-name parameters!"
}
