#!/usr/bin/env bash -xe

export SOURCEDIR=$(pwd)
export CNAME="izumi-build-$(date +%s)"
export RESOURCEDIR="$( cd "$(dirname "$0")" ; pwd -P )"
export HOMEDIR="$( cd ~ ; pwd -P )"

if [[ "$TRAVIS_PULL_REQUEST" == "false"  ]] ; then
  openssl aes-256-cbc -K $encrypted_8eadf24ba628_key -iv $encrypted_8eadf24ba628_iv -in secrets.tar.enc -out secrets.tar -d
  tar xvf secrets.tar
  ln -s .secrets/local.sbt local.sbt
fi

docker pull $IMAGE

docker run --rm --name $CNAME \
    -e TRAVIS_SCALA_VERSION=$TRAVIS_SCALA_VERSION \
    -e TRAVIS_BRANCH=$TRAVIS_BRANCH \
    -e TRAVIS_TAG=$TRAVIS_TAG \
    -e TRAVIS_PULL_REQUEST=$TRAVIS_PULL_REQUEST \
    -e TRAVIS_BUILD_NUMBER=$TRAVIS_BUILD_NUMBER \
    -e NPM_TOKEN=$NPM_TOKEN \
    -e NUGET_TOKEN=$NUGET_TOKEN \
    --volume "${SOURCEDIR}":/work:z \
    --volume "${HOMEDIR}":/root:z \
    $IMAGE bash -xe travis.sh $*

    #--volume $SSH_AUTH_SOCK:/ssh-agent --env SSH_AUTH_SOCK=/ssh-agent \
    #--volume $(cat $SOURCEDIR/.git/objects/info/alternates):/work/.git/objects:z \
