package izumi.fundamentals.platform.files

import izumi.fundamentals.platform.IzPlatformEffectfulUtil

trait IzFiles extends IzPlatformEffectfulUtil with FsGet

object IzFiles extends IzFiles
