FROM ubuntu:16.04
LABEL maintainer "jason.sj.cn@gmail.com"

RUN apt-get update \
  && apt-get install -y vim \
     wget \
     python2.7 \
     python-pip \
     redis-tools \
     dnsutils \
     ruby \
     rubygems \
  && gem install redis \
  && apt-get clean \
  && wget -O /usr/local/bin/redis-trib http://download.redis.io/redis-stable/src/redis-trib.rb \
  && chmod 755 /usr/local/bin/redis-trib
