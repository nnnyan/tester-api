FROM ubuntu:18.04
MAINTAINER p@nyan.ch

VOLUME "/tmp"

RUN apt update && \
    apt install -y software-properties-common && \
    add-apt-repository ppa:openjdk-r/ppa
RUN apt update && \
    apt install -y openjdk-11-jdk gcc g++ python3

RUN useradd -s /bin/sh -d /home/test -m tester && \
    chmod -R a-w /home/test

ADD ./build/libs/tester-api-0.0.1-SNAPSHOT.jar /root/app.jar

VOLUME "/root/logs"

RUN chmod -R go-rwx /root

EXPOSE 80
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/root/app.jar"]