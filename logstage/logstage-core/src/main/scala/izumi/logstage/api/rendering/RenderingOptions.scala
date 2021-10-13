package izumi.logstage.api.rendering

/**
  * @param withExceptions if `true`, print full stack trace of [[Throwable]]s in the interpolation
  * @param colored        if `true`, use colors in console output
  */
final case class RenderingOptions(
  withExceptions: Boolean,
  colored: Boolean,
  hideKeys: Boolean,
)
object RenderingOptions {
  def default: RenderingOptions = RenderingOptions(withExceptions = true, colored = true, hideKeys = false)
  def colorless: RenderingOptions = RenderingOptions(withExceptions = true, colored = false, hideKeys = false)
  def simple: RenderingOptions = RenderingOptions(withExceptions = false, colored = false, hideKeys = false)
}
