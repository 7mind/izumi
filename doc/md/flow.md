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
    ├── gnupg
    │   ├── pubring.gpg
    │   └── secring.gpg
    ├── local.sbt
    ├── travis-deploy-key
    └── travis-deploy-key.pub


And also you must have a credentials file in `~/.sbt`:

    ~/.sbt/secrets/credentials.sonatype-nexus.properties

- Credentials file contains your Sonatype credentials 
- `local.sbt` contains PGP secrets 
- GnuPG keys are required to sign artifacts before publishing to Central.
- OpenSSH key is required to push sbt-site back to the repo during travis build.

`/keys.sh` generates GPG keys and corresponding `local.sbt` file

So, the whole sequence to setup the project for publishing is:
    
    ./keys.sh
    cp doc/samples/credentials.sonatype-nexus.properties ~/.sbt/secrets/
    nano ~/.sbt/secrets/credentials.sonatype-nexus.properties

Openssl notes
-------------

    export OPENSSL_KEY=`openssl rand -hex 64`
    export OPENSSL_IV=`openssl rand -hex 16`
    echo "$OPENSSL_KEY"
    echo "$OPENSSL_IV"
    tar cvf secrets.tar -v --exclude=gnupg.home .secrets
    openssl aes-256-cbc -K ${OPENSSL_KEY} -iv ${OPENSSL_IV} -in secrets.tar -out secrets.tar.enc

Travis notes
------------

    tar cvf secrets.tar -v --exclude=gnupg.home .secrets
    travis encrypt-file secrets.tar
    

Multiple origins
----------------

Modify `.git/config`:

    [remote "origin"]
        url = git@github.com:7mind/izumi.git
        pushurl = git@github.com:7mind/izumi.git
        pushurl = git@bitbucket.org:7mind/izumi.git
        fetch = +refs/heads/*:refs/remotes/origin/*

