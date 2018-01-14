[![Build Status](https://travis-ci.org/pshirshov/izumi-r2.svg?branch=develop)](https://travis-ci.org/pshirshov/izumi-r2)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.pshirshov.izumi/izumi-r2_2.12.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.github.pshirshov.izumi%22)
[![Sonatype snapshots](https://img.shields.io/nexus/s/https/oss.sonatype.org/com.github.pshirshov.izumi/izumi-r2_2.12.svg)](https://oss.sonatype.org/content/repositories/snapshots/com/github/pshirshov/izumi/izumi-r2_2.12/)
[![Latest Release](https://img.shields.io/github/tag/pshirshov/izumi-r2.svg)](https://github.com/pshirshov/izumi-r2/releases)
[![License](https://img.shields.io/github/license/pshirshov/izumi-r2.svg)](https://github.com/pshirshov/izumi-r2/blob/develop/LICENSE)


Build options
-------------

1. `build.publish.overwrite` - enable stable artifact reuploading
2. `build.coursier.use` - enable Coursier resolver (not available in dependent projects)
3. `build.coursier.version` - override Coursier version (not available in dependent projects)

Project flow
============

Releases
--------

1. Use `sbt "release release-version 0.4.2 next-version 0.4.3-SNAPSHOT"`. You may use `skip-tests` flag in case you need it
2. `git checkout v1.0.99`
3. `sbt publishSigned sonatypeRelease` 


Travis notes
------------

    gpg --homedir ./.gnupg.home --full-generate-key
    gpg --homedir ./.gnupg.home --edit-key <email> addkey save
    gpg --homedir ./.gnupg.home --list-keys
    gpg --homedir ./.gnupg.home --export-secret-keys > .gnupg/secring.gpg
    gpg --homedir ./.gnupg.home --export > .gnupg/pubring.gpg
    gpg --homedir ./.gnupg.home --keyserver hkp://ipv4.pool.sks-keyservers.net --send-keys <keyid>
    gpg --homedir ./.gnupg.home --keyserver hkp://ipv4.pool.sks-keyservers.net --send-keys <subkeyid>
    
    tar cvf secrets.tar .gnupg credentials.sonatype-nexus.properties local.sbt
    travis encrypt-file secrets.tar

Multiple origins
----------------

Modify `.git/config`:

    [remote "origin"]
        url = git@github.com:pshirshov/izumi-r2.git
        pushurl = git@github.com:pshirshov/izumi-r2.git
        pushurl = git@bitbucket.org:pshirshov/izumi-r2.git
        fetch = +refs/heads/*:refs/remotes/origin/*
