package com.github.pshirshov.izumi.distage.config.model.exceptions

import com.github.pshirshov.izumi.distage.config.TranslationResult
import com.github.pshirshov.izumi.distage.model.exceptions.DIException

class ConfigTranslationException(message: String, val translationErrors: Seq[TranslationResult]) extends DIException(message, null)
