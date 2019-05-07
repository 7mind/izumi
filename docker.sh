#!/usr/bin/env bash -xe

export IMAGE="septimalmind/izumi-env:jdk11-5"
export SOURCEDIR=$(pwd)
export CNAME="izumi-build-$(date +%s)"
export RESOURCEDIR="$( cd "$(dirname "$0")" ; pwd -P )"
export HOMEDIR="$( cd ~ ; pwd -P )"

if [[ "$SYSTEM_PULLREQUEST_PULLREQUESTNUMBER" == ""  ]] ; then
    export TRAVIS_PULL_REQUEST=false
else
    export TRAVIS_PULL_REQUEST=true
fi

export TRAVIS_BRANCH=$(echo $BUILD_SOURCEBRANCH | sed -E "s/refs\/heads//")
export TRAVIS_TAG=$BUILD_SOURCEBRANCH

if [[ "$TRAVIS_PULL_REQUEST" == "false"  ]] ; then
    openssl aes-256-cbc -K ${OPENSSL_KEY} -iv ${OPENSSL_IV} -in secrets.tar.enc -out secrets.tar -d
    tar xvf secrets.tar
    ln -s .secrets/local.sbt local.sbt
fi

env

docker pull $IMAGE

env

docker run --rm --name $CNAME \
  -e TRAVIS_BRANCH=${TRAVIS_BRANCH} \
  -e TRAVIS_TAG=${TRAVIS_TAG} \
  -e TRAVIS_PULL_REQUEST=${TRAVIS_PULL_REQUEST} \
  -e TRAVIS_BUILD_NUMBER=${BUILD_BUILDID} \
  -e NPM_TOKEN=${TOKEN_NPM} \
  -e NUGET_TOKEN=${TOKEN_NUGET} \
  -e CODECOV_TOKEN=${TOKEN_CODECOV} \
  --volume "${SOURCEDIR}":/work:z \
  --volume "${HOMEDIR}":/root:z \
  $IMAGE bash -xe travis.sh coverage

#
#export SOURCEDIR=$(pwd)
#export CNAME="izumi-build-$(date +%s)"
#export RESOURCEDIR="$( cd "$(dirname "$0")" ; pwd -P )"
#export HOMEDIR="$( cd ~ ; pwd -P )"
#
#if [[ "$TRAVIS_PULL_REQUEST" == "false"  ]] ; then
#  openssl aes-256-cbc -K $encrypted_0429e73c206c_key -iv $encrypted_0429e73c206c_iv -in secrets.tar.enc -out secrets.tar -d
#  tar xvf secrets.tar
#  ln -s .secrets/local.sbt local.sbt
#fi
#
#docker pull $IMAGE
#
#docker run --rm --name $CNAME \
#    -e TRAVIS_SCALA_VERSION=$TRAVIS_SCALA_VERSION \
#    -e TRAVIS_BRANCH=$TRAVIS_BRANCH \
#    -e TRAVIS_TAG=$TRAVIS_TAG \
#    -e TRAVIS_PULL_REQUEST=$TRAVIS_PULL_REQUEST \
#    -e TRAVIS_BUILD_NUMBER=$TRAVIS_BUILD_NUMBER \
#    -e NPM_TOKEN=$NPM_TOKEN \
#    -e NUGET_TOKEN=$NUGET_TOKEN \
#    --volume "${SOURCEDIR}":/work:z \
#    --volume "${HOMEDIR}":/root:z \
#    $IMAGE bash -xe travis.sh $*
#
#    #--volume $SSH_AUTH_SOCK:/ssh-agent --env SSH_AUTH_SOCK=/ssh-agent \
#    #--volume $(cat $SOURCEDIR/.git/objects/info/alternates):/work/.git/objects:z \
