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

