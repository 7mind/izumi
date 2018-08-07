package com.github.pshirshov.izumi.logstage.config.exceptions

class MissedPropertyException(message: String) extends LogstageException(message, None) {
}

class UndefinedPropertyException(message: String) extends LogstageException(message, None) {
}

