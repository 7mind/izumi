package izumi.fundamentals.platform.files

import java.net.URI
import java.nio.file.*
import scala.annotation.nowarn
import scala.jdk.CollectionConverters.*
import scala.util.Try

@nowarn("msg=Unused import")
object IzFiles extends RecursiveFileRemovals with FileSearch with FsRefresh with FileReads with ExecutableSearch with Homedir with FileAttributes {
  def getFs(uri: URI, loader: ClassLoader): Try[FileSystem] = {
    // this is like DCL, there might be races but we have no tool to prevent them
    // so first we try to get a filesystem, if it's not there we try to create it, there might be a race so it can fail, so we try to get again
    Try(FileSystems.getFileSystem(uri))
      .orElse(Try(FileSystems.newFileSystem(uri, Map.empty[String, Any].asJava, loader)))
      .orElse(Try(FileSystems.getFileSystem(uri)))
  }

}
