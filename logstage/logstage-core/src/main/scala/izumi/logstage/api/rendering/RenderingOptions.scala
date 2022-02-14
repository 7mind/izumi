package izumi.logstage.api.rendering

/**
  * @param withExceptions if `true`, print full stack trace of [[Throwable]]s in the interpolation
  * @param colored        if `true`, use colors in console output
  */
final case class RenderingOptions(
  withExceptions: Boolean = true,
  colored: Boolean = true,
  hideKeys: Boolean = false,
)
object RenderingOptions {
  def default: RenderingOptions = RenderingOptions() // NOTE: Use this method in code – the default parameters in constructor are only there for pureconfig derivation
  def colorless: RenderingOptions = RenderingOptions(colored = false)
  def simple: RenderingOptions = RenderingOptions(withExceptions = false, colored = false)
}
