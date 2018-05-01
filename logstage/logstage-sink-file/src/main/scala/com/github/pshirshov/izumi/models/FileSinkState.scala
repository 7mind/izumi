package com.github.pshirshov.izumi.models

import com.github.pshirshov.izumi.FileSink.FileIdentity

case class FileSinkState(maxSize: Int,
                         path: String,
                         currentFileId: Option[FileIdentity] = None,
                         forRotate: Set[FileIdentity] = Set.empty,
                         currentFileSize: Int = 0
                        )
