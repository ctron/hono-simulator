FROM centos:7

MAINTAINER Jens Reimann <jreimann@redhat.com>
LABEL maintainer="Jens Reimann <jreimann@redhat.com>"

ARG NETTY_TCNATIVE_VERSION=2.0.17.Final

RUN yum update -y
RUN yum install -y centos-release-scl
RUN yum install -y java-11-openjdk java-11-openjdk-devel rh-maven35 iproute git
RUN yum install -y lsof

ENV \
    NETTY_TCNATIVE_VERSION=${NETTY_TCNATIVE_VERSION}

RUN yum install -y apr

RUN \
    yum install -y git autoconf automake libtool which apr-devel make openssl-devel && \
    git clone https://github.com/netty/netty-tcnative && \
    cd netty-tcnative && \
    git checkout netty-tcnative-parent-$NETTY_TCNATIVE_VERSION && \
    cd .. && \
    scl enable rh-maven35 "mvn -B clean install -f netty-tcnative/openssl-dynamic/pom.xml -am" && \
    install -m 0755 netty-tcnative/openssl-dynamic/target/native-lib-only/META-INF/native/linux64/libnetty_tcnative.so /usr/lib64/libnetty_tcnative.so && \
    rm -Rf ~/.m2/repository && \
    rm -Rf netty-tcnative && \
    yum -y history undo last && yum -y clean all && rm -rf /var/cache/yum

# switch to Java 11 as default

RUN \
    alternatives --set java $(readlink -f /usr/lib/jvm/java-11-openjdk/bin/java) && \
    alternatives --set javac $(readlink -f /usr/lib/jvm/java-11-openjdk/bin/javac) && \
    true

ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk
RUN echo ${JAVA_HOME}
RUN java -version
