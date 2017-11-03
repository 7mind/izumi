[![Build Status](https://travis-ci.org/pshirshov/izumi-r2.svg?branch=develop)](https://travis-ci.org/pshirshov/izumi-r2)

Build options
-------------

1. `build.publish.overwrite` - enable stable artifact reuploading
2. `build.coursier.use` - enable Coursier resolver (not available in dependent projects)
3. `build.coursier.version` - override Coursier version (not available in dependent projects)

Releases
--------

1. Use `release release-version 1.0.99 next-version 1.2.0-SNAPSHOT` or `release release-version 1.0.99 next-version 1.2.0-SNAPSHOT skip-tests`  
2. `git checkout v1.0.99`
3. `sbt publishSigned sonatypeRelease` 


Travis notice
-------------

    gpg --homedir ./.gnupg.home --full-generate-key
    gpg --homedir ./.gnupg.home --edit-key <email> addkey save
    gpg --homedir ./.gnupg.home --list-keys
    gpg --homedir ./.gnupg.home --export-secret-keys > .gnupg/secring.gpg
    gpg --homedir ./.gnupg.home --export > .gnupg/pubring.gpg
    gpg --homedir ./.gnupg.home --keyserver hkp://ipv4.pool.sks-keyservers.net --send-keys <keyid>
    gpg --homedir ./.gnupg.home --keyserver hkp://ipv4.pool.sks-keyservers.net --send-keys <subkeyid>
    
    tar cvf secrets.tar .gnupg credentials.sonatype-nexus.properties local.sbt
    travis encrypt-file secrets.tar
