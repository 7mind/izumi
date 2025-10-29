Build notes
===========

Prerequisites
-------------

```bash
nix develop
```

or with `direnv`:

```bash
direnv allow
```

Docs
----

```bash
sbt microsite/makeSite
```

Build options
-------------

1. `build.publish.overwrite` - enable stable artifact reuploading
