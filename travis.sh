#!/bin/bash -xe

#  - sbt clean coverage test coverageReport && sbt coverageAggregate
#after_success:
#  - sbt coveralls

function build {

if [[ "$TRAVIS_BRANCH" != "master" &&  "$TRAVIS_BRANCH" != "develop" && ! ( "$TRAVIS_TAG" =~ ^v.*$ ) ]] ; then
    sbt ++$TRAVIS_SCALA_VERSION "addVersionSuffix $TRAVIS_BRANCH"
fi

if [[ -f credentials.sonatype-nexus.properties ]] ; then
    sbt +clean +test +scripted +publishSigned

    if [[ "$TRAVIS_TAG" =~ ^v.*$ ]] ; then
        sbt ++$TRAVIS_SCALA_VERSION sonatypeRelease
    fi
else
    sbt +clean +test +scripted +package
fi


sbt clean coverage test coverageReport

}


function report {
    bash <(curl -s https://codecov.io/bash)
}

PARAMS=()
SOFT=0
SKIP=()
for i in "$@"
do
case $i in
    build)
        build
    ;;
    report)
        report
    ;;
    *)
        echo "Unknown option"
        exit 1
    ;;
esac
done