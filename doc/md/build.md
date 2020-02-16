Build notes
===========

Prerequisites
-------------

On mac:

```bash
brew tap caskroom/versions
brew update
brew install homebrew/cask/java sbt
```

Docs
----

```bash
sbt 'mdoc --no-link-hygiene --watch'
```

Build options
-------------

1. `build.publish.overwrite` - enable stable artifact reuploading
