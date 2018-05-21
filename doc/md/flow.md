Project flow
============

Releases
--------

1. Use `sbt "release release-version 0.4.2 next-version 0.4.3-SNAPSHOT"`. You may use `skip-tests` flag in case you need it
2. `git checkout v1.0.99`
3. `sbt publishSigned sonatypeRelease` 

Secrets
-------

Before you may perform a release, you need to create `.secrets` directory with the following structure:

    .secrets
    ├── credentials.sonatype-nexus.properties
    ├── gnupg
    │   ├── pubring.gpg
    │   └── secring.gpg
    ├── local.sbt
    ├── travis-deploy-key
    └── travis-deploy-key.pub


- Credentials file contains your Sonatype credentials 
- `local.sbt` contains PGP secrets 
- GnuPG keys are required to sign artifacts before publishing to Central.
- OpenSSH key is required to push sbt-site back to the repo during travis build.


So, the whole sequence to setup the project for publishing is:

    mkdir -p .secrets

    cp doc/samples/credentials.sonatype-nexus.properties .secrets/
    cp doc/samples/local.sbt .secrets/
    
    ln -s .secrets/local.sbt local.sbt

    nano .secrets/local.sbt
    nano .secrets/credentials.sonatype-nexus.properties
    
    ssh-keygen -t rsa -b 4096 -C "sbt-site@travis" -f .secrets/travis-deploy-key

    gpg --homedir ./.secrets/gnupg.home --full-generate-key
    gpg --homedir ./.secrets/gnupg.home --edit-key <email> addkey save
    gpg --homedir ./.secrets/gnupg.home --list-keys
    gpg --homedir ./.secrets/gnupg.home --export-secret-keys > .gnupg/secring.gpg
    gpg --homedir ./.secrets/gnupg.home --export > ./.secrets/gnupg
    gpg --homedir ./.secrets/gnupg.home --keyserver hkp://ipv4.pool.sks-keyservers.net --send-keys <keyid>
    gpg --homedir ./.secrets/gnupg.home --keyserver hkp://ipv4.pool.sks-keyservers.net --send-keys <subkeyid>
    



Travis notes
------------

    tar cvf secrets.tar -v --exclude=gnupg.home .secrets
    travis encrypt-file secrets.tar
    

Multiple origins
----------------

Modify `.git/config`:

    [remote "origin"]
        url = git@github.com:pshirshov/izumi-r2.git
        pushurl = git@github.com:pshirshov/izumi-r2.git
        pushurl = git@bitbucket.org:pshirshov/izumi-r2.git
        fetch = +refs/heads/*:refs/remotes/origin/*

