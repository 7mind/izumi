package izumi.distage.testkit.spec

private[spec] object SessionEnvironmentFixturePlatform extends ConcurrentSessionEnvironmentFixture {
  def scannedOwners(): Vector[(String, Boolean)] = Vector.empty
}
