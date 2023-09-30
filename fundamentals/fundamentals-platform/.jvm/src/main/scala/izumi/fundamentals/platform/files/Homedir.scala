package izumi.fundamentals.platform.files

import java.nio.file.{Path, Paths}

trait Homedir {
  def home(): Path = {
    Paths.get(System.getProperty("user.home"))
  }

  def homedir(): String = {
    home().toFile.getCanonicalPath
  }
}
