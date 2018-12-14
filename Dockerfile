FROM simulator-base:latest

MAINTAINER Jens Reimann <jreimann@redhat.com>
LABEL maintainer="Jens Reimann <jreimann@redhat.com>"

# prepare build

RUN mkdir /build

# start building

COPY . /build

RUN scl enable rh-maven35 "cd build && mvn -B clean package -DskipTests"
