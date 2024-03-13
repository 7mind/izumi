package izumi.fundamentals.platform.resources

case class GitStatus(branch: String, repoClean: Boolean, revision: String) {
  override def toString: String = {
    val out = s"""$branch#$revision"""
    if (repoClean) {
      out
    } else {
      s"$out*"
    }
  }
}
