package com.github.pshirshov.izumi.models

import com.github.pshirshov.izumi.FileSink.FileIdentity

case class FileSinkState(currentFileId: FileIdentity,
                         currentFileSize: Int,
                         forRotate: Set[FileIdentity] = Set.empty
                        )
