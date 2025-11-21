package izumi.fundamentals.platform

import java.nio.file.Path
import scala.annotation.tailrec

package object build {

  @tailrec
  private[build] def findProjectRoot(cp: Path): Option[Path] = {
    if (cp.resolve("build.sbt").toFile.exists()) {
      Some(cp)
    } else {
      val parent = cp.getParent

      if (parent == null || parent == cp.getRoot) {
        None
      } else {
        findProjectRoot(parent)
      }
    }
  }

}
