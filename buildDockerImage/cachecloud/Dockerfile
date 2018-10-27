#################################
#version: 1.2
#desc: cachecloud 镜像
#################################
FROM centos:7.2.1511

MAINTAINER shaoj@chinatelecom.cn

ENV CACHECLOUD_VERSION=1.2 \
    base_dir="/opt/cachecloud-web"
WORKDIR ${base_dir}
#COPY localtime /etc/
COPY cachecloud-${CACHECLOUD_VERSION} /tmp/cachecloud-${CACHECLOUD_VERSION}/

RUN    yum install -y openssh-server openssh-clients java-1.8.0-openjdk maven \
    && yum clean all \
    && sed -i 's/#   StrictHostKeyChecking ask/StrictHostKeyChecking no/' /etc/ssh/ssh_config \
    && cd /tmp/cachecloud-${CACHECLOUD_VERSION} \
    && mvn clean compile install -Ponline \
    && mkdir -p ${base_dir}/logs \
    && cp /tmp/cachecloud-${CACHECLOUD_VERSION}/cachecloud-open-web/target/cachecloud-open-web-1.0-SNAPSHOT.war ${base_dir}/ \
    && cp /tmp/cachecloud-${CACHECLOUD_VERSION}/cachecloud-open-web/src/main/resources/cachecloud-web.conf ${base_dir}/ 
VOLUME ${base_dir}
CMD  ["bash","/opt/cachecloud-web/cachecloud-open-web-1.0-SNAPSHOT.war","start"]
EXPOSE 8585
