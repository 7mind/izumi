package com.github.pshirshov.izumi.models

case class FileSinkConfig(fileSizeLimit: Int, destination: String, rotation: Rotation)

