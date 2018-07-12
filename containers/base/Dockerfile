FROM centos:7

MAINTAINER Jens Reimann <jreimann@redhat.com>
LABEL maintainer "Jens Reimann <jreimann@redhat.com>"

RUN yum update -y
RUN yum install -y centos-release-scl
RUN yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel rh-maven35 iproute git
RUN alternatives --auto java --verbose && java -version
