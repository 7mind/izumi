package com.github.pshirshov.izumi

import com.github.pshirshov.izumi.models.LogFile.{DummyFile, RealFile}

package object models {
  type FileId = Int
  type Files = scala.collection.mutable.Map[FileId, RealFile]

  object FileId {
    final val init = 0
  }

  object FileSize {
    final val undefined = -1
    final val init = 0
  }
}
