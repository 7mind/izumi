package izumi.fundamentals.platform.files

import izumi.fundamentals.platform.IzPlatformEffectfulUtil

trait IzFiles
  extends IzPlatformEffectfulUtil
  with RecursiveFileRemovals
  with FileSearch
  with FsRefresh
  with FileOps
  with ExecutableSearch
  with Homedir
  with FileAttributes
  with FsGet

object IzFiles extends IzFiles {}
