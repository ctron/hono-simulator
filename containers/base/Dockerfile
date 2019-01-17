FROM centos:7

MAINTAINER Jens Reimann <jreimann@redhat.com>
LABEL maintainer="Jens Reimann <jreimann@redhat.com>"

RUN yum update -y
RUN yum install -y centos-release-scl
RUN yum install -y java-1.8.0-openjdk java-1.8.0-openjdk-devel rh-maven35 iproute git
RUN yum install -y lsof

ENV \
    NETTY_TCNATIVE_VERSION=2.0.10.Final

RUN yum install -y apr

RUN \
    yum install -y git autoconf automake libtool which apr-devel make openssl-devel && \
    git clone https://github.com/netty/netty-tcnative && \
    cd netty-tcnative && \
    git checkout netty-tcnative-parent-$NETTY_TCNATIVE_VERSION && \
    cd .. && \
    scl enable rh-maven35 "mvn -B clean install -f netty-tcnative/openssl-dynamic/pom.xml -am" && \
    install -m 0755 netty-tcnative/openssl-dynamic/target/native-lib-only/META-INF/native/linux64/libnetty_tcnative.so /usr/lib64/libnetty_tcnative.so

