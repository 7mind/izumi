package izumi.logstage.api.rendering

/**
  * @param withExceptions if `true`, print full stack trace of [[Throwable]]s in the interpolation
  * @param colored        if `true`, use colors in console output
  */
final case class RenderingOptions(
  withExceptions: Boolean,
  colored: Boolean,
)
object RenderingOptions {
  def default: RenderingOptions = RenderingOptions(withExceptions = true, colored = true)
  def colorless: RenderingOptions = RenderingOptions(withExceptions = true, colored = false)
  def simple: RenderingOptions = RenderingOptions(withExceptions = false, colored = false)
}
