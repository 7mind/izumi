package izumi.fundamentals.platform.graphs.dotml

import java.io.File

import izumi.fundamentals.graphs.dotml.GraphVizDotML
import izumi.fundamentals.platform.language.Quirks
import izumi.fundamentals.platform.os.{IzOs, OsType}

import scala.language.postfixOps

/**
  * @author Depeng Liang
  *
  * https://github.com/Ldpe2G/Graphviz4S/blob/master/src/main/scala/com/liangdp/graphviz4s/Dot.scala
  */
object Backend {

  /**
    * Save the source to file and render with the Graphviz engine.
    * @param engine The layout commmand used for rendering ('dot', 'neato', ...).
    * @param format The output format used for rendering ('pdf', 'png', ...).
    * @param fileName Name of the DOT source file to render.
    * @param directory Directory to save the Dot source file.
    * @param view Whether open the rendered result with the default application.
    * @param cleanup Whether delete the source file after rendering.
    * @return The (possibly relative) path of the rendered file.
    */
  def render(
    graphVizDotML: GraphVizDotML,
    engine: String = "dot",
    format: String = "pdf",
    fileName: String,
    directory: String,
    view: Boolean = false,
    cleanUp: Boolean = false,
  ): String = {
    val filePath = graphVizDotML.save(fileName, directory)
    val rendered = doRender(engine, format, filePath)
    if (cleanUp) new File(filePath).delete
    if (view) doView(rendered)
    rendered
  }

  /**
    * Save the source to file, open the rendered result in a viewer.
    * @param engine The layout commmand used for rendering ('dot', 'neato', ...).
    * @param format The output format used for rendering ('pdf', 'png', ...).
    * @param fileName Name of the DOT source file to render.
    * @param directory Directory to save the Dot source file.
    * @param cleanup Whether delete the source file after rendering.
    * @return The (possibly relative) path of the rendered file.
    */
  def view(graphVizDotML: GraphVizDotML, engine: String = "dot", format: String = "pdf", fileName: String, directory: String, cleanUp: Boolean = false): String = {
    render(graphVizDotML, engine, format, fileName, directory, view = true, cleanUp)
  }

  // http://www.graphviz.org/cgi-bin/man?dot
  private val ENGINES = Set(
    "dot",
    "neato",
    "twopi",
    "circo",
    "fdp",
    "sfdp",
    "patchwork",
    "osage",
  )

  // http://www.graphviz.org/doc/info/output.html
  private val FORMATS = Set(
    "bmp",
    "canon",
    "dot",
    "gv",
    "xdot",
    "xdot1.2",
    "xdot1.4",
    "cgimage",
    "cmap",
    "eps",
    "exr",
    "fig",
    "gd",
    "gd2",
    "gif",
    "gtk",
    "ico",
    "imap",
    "cmapx",
    "imap_np",
    "cmapx_np",
    "ismap",
    "jp2",
    "jpg",
    "jpeg",
    "jpe",
    "pct",
    "pict",
    "pdf",
    "pic",
    "plain",
    "plain-ext",
    "png",
    "pov",
    "ps",
    "ps2",
    "psd",
    "sgi",
    "svg",
    "svgz",
    "tga",
    "tif",
    "tiff",
    "tk",
    "vml",
    "vmlz",
    "vrml",
    "wbmp",
    "webp",
    "xlib",
    "x11",
  )

  /**
    * Return command for execution and name of the rendered file.
    *
    * @param engine The layout commmand used for rendering ('dot', 'neato', ...).
    * @param format The output format used for rendering ('pdf', 'png', ...).
    * @param filePath The output path of the source file.
    * @return render command to execute.
    * @return rendered file path.
    */
  def command(engine: String, format: String, filePath: String = null): (String, String) = {
    require(ENGINES.contains(engine), s"unknown engine: $engine")
    require(FORMATS.contains(format), s"unknown format: $format")
    Option(filePath) match {
      case Some(path) => (s"$engine -T$format -O $path", s"$path.$format")
      case None => (s"$engine -T$format", null)
    }
  }

  /**
    * Render file with Graphviz engine into format,  return result filename.
    *
    * @param engine The layout commmand used for rendering ('dot', 'neato', ...).
    * @param format The output format used for rendering ('pdf', 'png', ...).
    * @param filepath Path to the DOT source file to render.
    */
  @throws(classOf[RuntimeException])
  def doRender(engine: String = "dot", format: String = "pdf", filePath: String): String = {
    val (args, rendered) = command(engine, format, filePath)
    import sys.process._
    try {
      args !
    } catch {
      case _: Throwable =>
        val errorMsg = s"""failed to execute "$args", """ +
          """"make sure the Graphviz executables are on your systems' path"""
        throw new RuntimeException(errorMsg)
    }
    rendered
  }

  /**
    * Open filepath with its default viewing application (platform-specific).
    * For know only support linux.
    */
  @throws(classOf[RuntimeException])
  def doView(filePath: String): Unit = {
    val command = IzOs.osType match {
      case OsType.Mac =>
        s"open $filePath"

      case OsType.Linux =>
        s"xdg-open $filePath"

      case OsType.Windows =>
        s"start $filePath"

      case OsType.Unknown =>
        throw new IllegalArgumentException(s"Unsupported OS")
    }

    import sys.process._
    try {
      Quirks.discard(command !)
    } catch {
      case _: Throwable =>
        val errorMsg = s"failed to execute $command"
        throw new RuntimeException(errorMsg)
    }
  }
}
