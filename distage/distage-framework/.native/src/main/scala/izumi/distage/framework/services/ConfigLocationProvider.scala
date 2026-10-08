package izumi.distage.framework.services

private[services] trait ConfigLocationProviderPlatformSpecific {
  protected final def configExtension: String = "json"
}
