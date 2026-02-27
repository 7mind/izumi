package izumi.fundamentals.platform.resources

/** Utility for parsing and matching glob patterns.
  *
  * Supports explicit base path syntax with braces, multiple include patterns,
  * exclusion patterns with ! prefix, and wildcards (* for single level, ** for recursive).
  */
object GlobParser {

  /** Parsed glob pattern representation */
  case class GlobPattern(
    basePath: String,
    includePatterns: List[String],
    excludePatterns: List[String]
  )

  /** Normalize path separators to forward slashes (for Windows compatibility) */
  def normalizeSeparators(path: String): String = path.replace('\\', '/')

  /** Parse glob expression like "basePath/{pattern1,pattern2,!exclude}"
    * or legacy "pattern" (base path extracted automatically)
    */
  def parseGlobExpr(pathExpr: String): GlobPattern = {
    val normalized = normalizeSeparators(pathExpr)
    // Check for explicit base path syntax: basePath/{patterns}
    val braceStart = normalized.indexOf('{')
    val braceEnd = normalized.lastIndexOf('}')

    if (braceStart != -1 && braceEnd != -1 && braceEnd > braceStart) {
      // Explicit base path syntax
      val basePath = normalized.substring(0, braceStart)
      val patternsStr = normalized.substring(braceStart + 1, braceEnd)
      val patterns = patternsStr.split(',').map(_.trim).filter(_.nonEmpty).toList

      val (excludePatterns, includePatterns) = patterns.partition(_.startsWith("!"))
      val cleanExcludes = excludePatterns.map(_.drop(1)) // Remove '!' prefix

      GlobPattern(basePath, includePatterns, cleanExcludes)
    } else {
      // Legacy syntax or simple pattern - extract base path automatically
      val patterns = normalized.split(',').map(_.trim).filter(_.nonEmpty).toList

      val (excludePatterns, includePatterns) = patterns.partition(_.startsWith("!"))
      val cleanExcludes = excludePatterns.map(_.drop(1)) // Remove '!' prefix

      // Extract base path from first include pattern (part before any wildcards)
      val basePath = if (includePatterns.nonEmpty) {
        findBasePath(includePatterns.head)
      } else {
        ""
      }

      // Remove base path from patterns to make them relative
      val relativeIncludes = includePatterns.map { p =>
        if (basePath.nonEmpty && p.startsWith(basePath + "/")) {
          p.substring(basePath.length + 1)
        } else if (basePath.nonEmpty && p.startsWith(basePath)) {
          p.substring(basePath.length)
        } else {
          p
        }
      }

      GlobPattern(basePath, relativeIncludes, cleanExcludes)
    }
  }

  /** Find base path (directory part before wildcards) */
  private def findBasePath(pattern: String): String = {
    val wildcardIdx = pattern.indexWhere(c => c == '*' || c == '?')
    if (wildcardIdx == -1) {
      // No wildcards - treat entire pattern as base path if it looks like a directory
      pattern
    } else {
      // Find last '/' before the wildcard
      val lastSlash = pattern.lastIndexOf('/', wildcardIdx)
      if (lastSlash == -1) "" else pattern.substring(0, lastSlash)
    }
  }

  /** Convert glob pattern to regex string */
  def globToRegex(pattern: String): String = {
    val sb = new StringBuilder("^")
    var i = 0

    while (i < pattern.length) {
      pattern(i) match {
        case '*' =>
          if (i + 1 < pattern.length && pattern(i + 1) == '*') {
            // double-star pattern - matches anything including /
            if (i + 2 < pattern.length && pattern(i + 2) == '/') {
              // double-star-slash pattern - match any path segments
              sb.append("(?:.*?/)?")
              i += 3
            } else if (i == 0 || pattern(i - 1) == '/') {
              // double-star at start or after / - match any path
              sb.append(".*?")
              i += 2
            } else {
              // double-star in middle without proper boundaries - treat as *
              sb.append("[^/]*?")
              i += 1
            }
          } else {
            // Single * - matches anything except /
            sb.append("[^/]*?")
            i += 1
          }
        case '?' =>
          sb.append("[^/]")
          i += 1
        case '.' | '(' | ')' | '+' | '|' | '^' | '$' | '@' | '%' | '[' | ']' | '{' | '}' =>
          sb.append('\\').append(pattern(i))
          i += 1
        case '\\' =>
          if (i + 1 < pattern.length) {
            sb.append('\\').append('\\')
            i += 1
          } else {
            sb.append("\\\\")
            i += 1
          }
        case c =>
          sb.append(c)
          i += 1
      }
    }

    sb.append('$').toString
  }

  /** Check if path matches a glob pattern */
  def matchesGlob(path: String, pattern: String): Boolean = {
    val regex = globToRegex(pattern)
    normalizeSeparators(path).matches(regex)
  }

  /** Check if path matches the parsed glob pattern (any include and no excludes) */
  def matchesPattern(path: String, glob: GlobPattern): Boolean = {
    glob.includePatterns.exists(pattern => matchesGlob(path, pattern)) &&
      !glob.excludePatterns.exists(pattern => matchesGlob(path, pattern))
  }
}
