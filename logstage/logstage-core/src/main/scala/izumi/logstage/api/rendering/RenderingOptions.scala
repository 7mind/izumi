package izumi.logstage.api.rendering

import izumi.logstage.api.rendering.logunits.BasicStyleTag

/**
  * @param withExceptions if `true`, print full stack trace of [[Throwable]]s in the interpolation
  * @param colored        if `true`, use colors in console output
  * @param hideKeys       if `true`, remove names of interpolated variables from the output
  * @param richFormatting if `true`, adds support of style tags(like <b>; <i>; etc.)
  */
final case class RenderingOptions(
  withExceptions: Boolean = true,
  colored: Boolean = true,
  hideKeys: Boolean = false,
  richFormatting: Boolean = false,
  richStylesheet: Map[String, Seq[BasicStyleTag]] = Map.empty,
)
object RenderingOptions {
  def default: RenderingOptions = RenderingOptions() // NOTE: Use this method in code – the default parameters in constructor are only there for pureconfig derivation
  def colorless: RenderingOptions = RenderingOptions(colored = false)
  def simple: RenderingOptions = RenderingOptions(withExceptions = false, colored = false)
  def rich(stylesheet: Map[String, Seq[BasicStyleTag]]): RenderingOptions = RenderingOptions(richFormatting = true, richStylesheet = stylesheet)
}
