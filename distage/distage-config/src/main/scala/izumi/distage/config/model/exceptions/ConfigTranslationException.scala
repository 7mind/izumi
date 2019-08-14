package izumi.distage.config.model.exceptions

import izumi.distage.config.TranslationResult
import izumi.distage.model.exceptions.DIException

class ConfigTranslationException(message: String, val translationErrors: Seq[TranslationResult]) extends DIException(message, null)
