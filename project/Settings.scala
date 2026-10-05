object DocKeys {
  lazy val prefix = sbt.settingKey[String => String]("")

  val snapshotSiteRoot: String = "latest"
  val snapshotSitePrefix: String = s"$snapshotSiteRoot/snapshot"
  val preservedSiteFiles: Set[String] = Set("CNAME", ".nojekyll", "README.md")
}
