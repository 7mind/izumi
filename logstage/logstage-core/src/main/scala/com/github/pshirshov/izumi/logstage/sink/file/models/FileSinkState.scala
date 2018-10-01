package com.github.pshirshov.izumi.logstage.sink.file.models

import com.github.pshirshov.izumi.logstage.sink.file.FileSink.FileIdentity


case class FileSinkState(
                          currentFileId: FileIdentity
                          , currentFileSize: Int
                          , forRotate: Set[FileIdentity] = Set.empty
                        )
