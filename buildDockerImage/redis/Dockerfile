FROM centos:7.2.1511

LABEL maintainer "jason.sj.cn@gmail.com"

EXPOSE 6379
ENTRYPOINT ["/entrypoint.sh"]
CMD ["redis-server", "/etc/redis/redis.conf"]
VOLUME ["/var/lib/redis"]
WORKDIR /var/lib/redis

ENV REDIS_VERSION=3.0.7
COPY entrypoint.sh /entrypoint.sh

RUN    yum install -y openssh-server openssh-clients iproute wget make gcc tcl \
    && yum clean all \
    && ssh-keygen -t rsa -N '' -f /etc/ssh/ssh_host_rsa_key \
    && ssh-keygen -t dsa -N '' -f /etc/ssh/ssh_host_dsa_key \
    && ssh-keygen -t ecdsa -N '' -f /etc/ssh/ssh_host_ecdsa_key \
    && ssh-keygen -t ed25519 -N '' -f /etc/ssh/ssh_host_ed25519_key \
    && useradd cachecloud \
    && echo 'cachecloud' | passwd --stdin cachecloud \
    && cd /tmp \
    && wget -O /usr/local/bin/gosu https://github.com/tianon/gosu/releases/download/1.11/gosu-amd64 \
    && chmod +x /usr/local/bin/gosu \
    && wget http://download.redis.io/releases/redis-${REDIS_VERSION}.tar.gz \
    && tar xf redis-${REDIS_VERSION}.tar.gz \
    && cd /tmp/redis-${REDIS_VERSION} \
    && make distclean \
    && make \
    && make install \
    && cp redis.conf /etc/redis.conf \
    && sed -i -e 's/# bind 127.0.0.1/bind 0.0.0.0/' /etc/redis.conf \
    && adduser redis \
    && rm -rf /tmp/* \
    && chmod +x /entrypoint.sh
