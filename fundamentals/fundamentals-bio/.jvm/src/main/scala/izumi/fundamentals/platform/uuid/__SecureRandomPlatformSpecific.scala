package izumi.fundamentals.platform.uuid

private[uuid] object __SecureRandomPlatformSpecific {
  type SecureRandomImpl = java.security.SecureRandom
}
