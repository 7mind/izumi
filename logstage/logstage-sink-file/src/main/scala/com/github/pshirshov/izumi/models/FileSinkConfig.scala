package com.github.pshirshov.izumi.models

import com.github.pshirshov.izumi.Rotation

case class FileSinkConfig(fileSizeLimit: Int, destination: String, rotation: Rotation)

