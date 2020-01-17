package izumi.distage.framework.model

final class PluginSource private (
  private val packagesEnabled: List[String],
  packagesDisabled: List[String],
)

object PluginSource {
  def apply(): PluginSource = {
    final case class PluginSource(
      packagesEnabled: List[String] = Nil,
      packagesDisabled: List[String] = Nil,
    )

    ()
  }
}
