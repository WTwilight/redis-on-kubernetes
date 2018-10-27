# redis-on-kubernetes
将Redis部署到Kubernetes集群中，并通过cachecloud监控redis集群。  
通过下面的方式部署的redis集群只能为部署在Kubernetes集群内的应用提供redis服务。

# 组件说明
- redis实例：  
    使用Kubernetes的statefulset控制器部署redis实例。一个redis集群对应一个statefulset  
- redis集群管理工具redis-trib：  
    使用Kubernetes的Deployment控制器部署redis集群的管理工具redis-trib，可管理多个redis集群  
- redis可视化监控cachecloud：  
    使用Kubernetes的Deployment控制器部署cachecloud，可一个cachecloud监控多个redis集群或一对一监控  
- MySQL服务：  
    为cachecloud提供数据存储服务，使用deployment控制器部署。
- Ceph RBD：  
    为MySQL，redis实例提供持久化存储 


# 镜像制作
该项目中需要用到：  
- MySQL(5.7)  
- cachecloud(1.2)  
- redis(3.0.7)  
- redis-trib  

其中，MySQL可以直接使用官方提供的镜像，无需另外制作。  
接下来需要根据实际环境情况制作cachecloud，redis-trib及redis镜像。  

## 准备MySQL镜像
```
docker pull mysql:5.7
docker tag mysql:5.7 private.registry.com/library/mysql:5.7
docker push private.registry.com/library/mysql:5.7
```

## 制作cachecloud镜像
cachecloud是搜狐开源的redis集群管理及监控工具，这里我们只用到其监控功能，即后台创建好redis集群后，再将redis集群信息导入cachecloud，实现WEB监控。  
cachecloud不提供动态配置文件，即需要修改好相关参数后，再用maven打成war包，最后运行war包。  
由于需要先修改相关配置再maven打包，这里有有一项是必须需要修改的，即MySQL的配置，具体操作过程如下：  
官方下载最新版的cachecloud release版：  
```
wget https://github.com/sohutv/cachecloud/archive/1.2.tar.gz
```
解压并修改MySQL相关参数，需要提前确认好MySQL环境，主要修改的有3个参数：
- cachecloud.db.url  
- cachecloud.db.user
- cachecloud.db.password
```
tar xf 1.2.tar.gz
vim cachecloud-1.2/cachecloud-open-web/src/main/swap/online.properties
  cachecloud.db.url = jdbc:mysql://cachecloud-mysql:3306/cachecloud
  cachecloud.db.user = admin
  cachecloud.db.password = admin
  cachecloud.maxPoolSize = 20
  
  isClustered = true
  isDebug = false
  spring-file=classpath:spring/spring-online.xml
  log_base=/opt/cachecloud-web/logs
  web.port=8585
  log.level=WARN
```

编写Dockerfile：  
**说明**：cachecloud服务需要使用ssh服务来管理和监控redis实例，因此需要安装ssh客户端，同样，需要在redis镜像中安装好sshd，并创建好cachecloud用户。  
```
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
```

将Dockerfile和修改好配置的cachecloud-1.2源码包放在同一目录，执行以下命令制作cachecloud镜像：  
```
docker build -t private.registry.com/library/cachecloud:1.2-centos.7.2.1511 .
```

将镜像push到私有仓库：  
```
docker push private.registry.com/library/cachecloud:1.2-centos.7.2.1511
```

## 制作redis-trib镜像
Dockerfile：  
```
FROM ubuntu:16.04
LABEL maintainer "shaoj@chinatelecom.cn"

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
```

制作redis-trib镜像：
```
docker build -t private.registry.com/library/redis-trib:stable .
```

## 制作redis镜像
Dockerfile：  
```
FROM centos:7.2.1511

LABEL maintainer "shaoj@chinatelecom.cn"

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
```

Dockerfile中用到的entrypoint.sh脚本：  
**说明**：脚本中的`/usr/sbin/sshd &` 目的是想启动sshd服务，但实践证明，这么写之后，运行容器时，容器内的sshd并没有运行，查阅资料，用supervisor可以解决容器内同时运行多个进程的问题，这里没有实践，如有需要可自行实现。  
我这里测试环境的临时解决办法为：`docker exec -it container_id /bin/bash`进入redis容器的命令行，然后运行命令：`/usr/sbin/sshd`，然后`exit`退出容器命令行。虽然有点low，不过测试环境下凑效。回头找时间再把镜像改一下。  
```
#!/bin/bash

/usr/sbin/sshd &

if [[ "$1" == "redis-server" ]]; then
  chown -R redis:redis /var/lib/redis
  exec gosu redis "$@"
fi
exec "$@"
```

制作redis镜像：  
```
docker build -t private.registry.com/library/redis:3.0.7-centos7.2.1511 .
```

将镜像push到私有仓库：  
```
docker push private.registry.com/library/redis:3.0.7-centos7.2.1511
```

# 部署
## 配置持久化存储 - storageclass
使用ceph RBD做为storageclass的后端存储，需要有ceph集群环境，并在ceph集群中创建用于创建storageclass的pool，示例中pool名为`kubernetes_pool`。  
1. 创建storageclass：  
```
# more cephrbd-sc.yaml 
kind: StorageClass
apiVersion: storage.k8s.io/v1
metadata:
  name: cephrbd-sc
provisioner: ceph.com/rbd
parameters:
  monitors: 10.164.0.97:6789,10.164.0.100:6789,10.164.0.101:6789
  adminId: admin
  adminSecretName: ceph-secret
  adminSecretNamespace: kube-system
  pool: kubernetes_pool
  userId: admin
  userSecretName: ceph-secret-user
  userSecretNamespace: default
  fsType: xfs
  imageFormat: "2"
  imageFeatures: "layering"
```

2. 创建RBD时使用的secret：  
```
# more secret-cephadmin.yaml 
apiVersion: v1
kind: Secret
metadata:
  name: ceph-secret
  namespace: kube-system
type: "kubernetes.io/rbd"  
data:
  # ceph auth get-key client.admin | base64
  key: QVFDekozcGI5TmcwTWhBQW53TmlQRzIxWmJTVFlLN1pKNERWekE9PQ==
```

3. map RBD时使用的secret：  
```
# more secret-cephuser.yaml                      
apiVersion: v1
kind: Secret
metadata:
  name: ceph-secret-user
  namespace: default
type: "kubernetes.io/rbd"  
data:
  # ceph auth get-key client.admin | base64
  key: QVFDekozcGI5TmcwTWhBQW53TmlQRzIxWmJTVFlLN1pKNERWekE9PQ==
```

说明：此次测试环境中创建RBD和map RBD使用的都是admin这个用户。需要注意的是，用于map RBD的secret必须和使用该sercet的应用部署在同一命名空间。  

4. 创建storageclass及secret：
```
kubectl apply -f cephrbd-sc.yaml
kubectl apply -f secret-cephadmin.yaml
kubectl apply -f secret-cephuser.yaml
```

5. 查看storageclass及secret
```
# kubectl get sc
NAME         PROVISIONER    AGE
cephrbd-sc   ceph.com/rbd   1d

# kubectl -n kube-system get secret ceph-secret 
NAME          TYPE                DATA      AGE
ceph-secret   kubernetes.io/rbd   1         1d

# kubectl get secret ceph-secret-user 
NAME               TYPE                DATA      AGE
ceph-secret-user   kubernetes.io/rbd   1         1d
```

## 部署MySQL 
mysql-deployment.yaml：  
```
apiVersion: v1
kind: Service
metadata:
  name: cachecloud-mysql
  labels:
    app: cachecloud
spec:
  ports:
    - port: 3306
  selector:
    app: cachecloud
    tier: mysql
  clusterIP: None
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: cachecloud-mysql-pvc
  labels:
    app: cachecloud
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: cephrbd-sc
  resources:
    requests:
      storage: 20Gi
---
apiVersion: apps/v1 
kind: Deployment
metadata:
  name: cachecloud-mysql
  labels:
    app: cachecloud
spec:
  selector:
    matchLabels:
      app: cachecloud
      tier: mysql
  strategy:
    type: Recreate
  template:
    metadata:
      labels:
        app: cachecloud
        tier: mysql
    spec:
      containers:
      - image: private.registry.com/library/mysql:5.7
        name: cachecloud-mysql
        env:
        - name: MYSQL_ROOT_PASSWORD
          valueFrom:
            secretKeyRef:
              name: mysql-pass
              key: password
        livenessProbe:
          tcpSocket:
            port: 3306
        ports:
        - containerPort: 3306
          name: cachecloudmysql
        volumeMounts:
        - name: cachecloud-mysql-storage
          mountPath: /var/lib/mysql
      volumes:
      - name: cachecloud-mysql-storage
        persistentVolumeClaim:
          claimName: cachecloud-mysql-pvc
```

创建MySQL应用（含service和PersistentVolumeClaim）：  
```
kubectl create secret generic mysql-pass --from-literal=password=12345678
kubectl apply -f mysql-deployment.yaml

# kubectl get deployment cachecloud-mysql 
NAME               DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
cachecloud-mysql   1         1         1            1           1d
```

初始化MySQL数据库：  
需要创建cachecloud使用的数据库，表，及用户密码，且需要与cachecloud中配置的一致：  
```
  cachecloud.db.url = jdbc:mysql://cachecloud-mysql:3306/cachecloud
  cachecloud.db.user = admin
  cachecloud.db.password = admin
```
即：  
数据库名：cachecloud  
用户名：admin  
密码：admin  

创建表，则使用cachecloud源码包中的cachecloud.sql创建，文件路径：`cachecloud-1.2/script/cachecloud.sql`。  
**注意**：  
在使用cachecloud.sql导入表之前，需要修改cachecloud.sql的两个地方：  
1. 所有存储IP的字段，字段长度需加长，建议改成100，否则cachecloud添加redis实例时会失败。  
2. 所有'0000-00-00 00:00:00'字符串需修改成有效的时间，可以是当前时间，比如'2018-10-20 16:43:00'，否则导入表时会因时间无效失败。  

具体操作：  
```
# 查询mysql的pod id：
# kubectl get pod | grep cachecloud-mysql
cachecloud-mysql-589f786c5f-sx5vn            1/1       Running                           0          5m

# 将修改过的cachecloud.sql拷贝到mysql容器中：  
kubectl cp cachecloud.sql cachecloud-mysql-589f786c5f-sx5vn:/cachecloud.sql   

# 进入mysql容器bash命令行：  
kubectl exec -it cachecloud-mysql-589f786c5f-sx5vn /bin/bash
root@cachecloud-mysql-589f786c5f-sx5vn:/#

连接数据库：  
root@cachecloud-mysql-589f786c5f-sx5vn:/# mysql -uroot -p12345678                                                                    
mysql: [Warning] Using a password on the command line interface can be insecure.
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 43858
Server version: 5.7.23 MySQL Community Server (GPL)

Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> 

创建cachecloud数据库及用户：
mysql> create database cachecloud;

mysql> grant all on cachecloud.* to 'admin'@'%' identified by 'admin';
mysql> grant all on cachecloud.* to 'admin'@'localhost' identified by 'admin';

mysql> flush privileges;

mysql> exit

使用admin用户登录mysql：
root@cachecloud-mysql-589f786c5f-sx5vn:/# mysql -uadmin -padmin
mysql> show databases;
+--------------------+
| Database           |
+--------------------+
| information_schema |
| cachecloud         |
+--------------------+
2 rows in set (0.00 sec)

切换到cachecloud数据库：
mysql> use cachecloud;

导入cachecloud表：
mysql> source /cachecloud.sql;
```

## 部署cachecloud：
cachecloud-deployment.yaml  
```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cachecloud
  labels:
    app: cachecloud
spec:
  replicas: 1
  selector:
    matchLabels:
      app: cachecloud
  template:
    metadata:
      labels:
        app: cachecloud
    spec:
      containers:
      - name: cachecloud
        image: private.registry.com/library/cachecloud:1.2-centos.7.2.1511
        ports:
        - containerPort: 8585

---
apiVersion: v1
kind: Service
metadata:
  name: cachecloud
  labels:
    app: cachecloud
spec:
  type: NodePort
  ports:
    - port: 8585
      protocol: TCP
      targetPort: 8585
      nodePort: 30085
  selector:
    app: cachecloud
```

部署：
```
kubectl apply -f cachecloud-deployment.yaml
```

访问cachecloud WEB界面：  
```
http://kubernetes-node-ip:30085
```

## 部署redis实例
configmap-redis.yaml  
```
apiVersion: v1
kind: ConfigMap
metadata:
  creationTimestamp: null
  name: redis-conf
  namespace: default
data:
  redis.conf: |
    appendonly yes
    cluster-enabled yes
    cluster-config-file /var/lib/redis/nodes.conf
    cluster-node-timeout 5000
    dir /var/lib/redis
    port 6379
    databases 16
    maxmemory 8gb
    appendfsync everysec
```

redis-headless.yaml  
```
apiVersion: v1
kind: Service
metadata:
  name: redis-lmx-service
  labels:
    app: redis-lmx
spec:
  ports:
  - name: redis-lmx-port
    port: 6379
  clusterIP: None
  selector:
    app: redis-lmx
    appCluster: redis-lmx-cluster
```

redis-access.yaml：  
```
apiVersion: v1
kind: Service
metadata:
  name: redis-lmx-access-service
  labels:
    app: redis-lmx
spec:
  ports:
  - name: redis-lmx-port
    protocol: "TCP"
    port: 6379
    targetPort: 6379
  selector:
    app: redis-lmx
    appCluster: redis-lmx-cluster
```

redis-statefulset.yaml：  
```
apiVersion: apps/v1beta1
kind: StatefulSet
metadata:
  name: redis-lmx
spec:
  serviceName: "redis-lmx-service"
  replicas: 3
  template:
    metadata:
      labels:
        app: redis-lmx
        appCluster: redis-lmx-cluster
    spec:
      terminationGracePeriodSeconds: 20
      affinity:
        podAntiAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
          - weight: 100
            podAffinityTerm:
              labelSelector:
                matchExpressions:
                - key: app
                  operator: In
                  values:
                  - redis-lmx
              topologyKey: kubernetes.io/hostname
      containers:
      - name: redis-lmx
        image: "private.registry.com/library/redis:3.0.7-centos7.2.1511"
        command:
          - "redis-server"
        args:
          - "/etc/redis/redis.conf"
        resources:
          requests:
            cpu: "250m"
            memory: "4096Mi"
          limits:
            cpu: "500m"
            memory: "8192Mi"
        ports:
            - name: redis-lmx
              containerPort: 6379
              protocol: "TCP"
            - name: cluster-lmx
              containerPort: 16379
              protocol: "TCP"
        volumeMounts:
          - name: "redis-conf"
            mountPath: "/etc/redis"
          - name: "redis-lmx-data"
            mountPath: "/var/lib/redis"
      volumes:
      - name: "redis-conf"
        configMap:
          name: "redis-conf"
          items:
            - key: "redis.conf"
              path: "redis.conf"
  volumeClaimTemplates:
  - metadata:
      name: redis-lmx-data
    spec:
      accessModes: [ "ReadWriteOnce" ]
      storageClassName: cephrbd-sc
      resources:
        requests:
          storage: 50Gi
```

创建redis实例：  
```
kubectl apply -f configmap-redis.yaml
kubectl apply -f redis-headless.yaml
kubectl apply -f redis-access.yaml
kubectl apply -f redis-statefulset.yaml
```

查询pod运行状态：  
```
# kubectl get pod -l app=redis-whx -o wide    
NAME          READY     STATUS    RESTARTS   AGE       IP             NODE
redis-whx-0   1/1       Running   0          1d        10.244.3.45    gz-bigdata-164000105.ctc.local
redis-whx-1   1/1       Running   0          1d        10.244.0.116   gz-bigdata-164000102.ctc.local
redis-whx-2   1/1       Running   0          1d        10.244.2.82    gz-bigdata-164000104.ctc.local
redis-whx-3   1/1       Running   0          1d        10.244.1.15    gz-bigdata-164000103.ctc.local
redis-whx-4   1/1       Running   0          1d        10.244.3.46    gz-bigdata-164000105.ctc.local
redis-whx-5   1/1       Running   0          1d        10.244.2.83    gz-bigdata-164000104.ctc.local
```

## 部署redis-trib
直接使用`kubectl run`运行redis-trib
```
kubectl run redis-trib -i -tty --image=private.registry.com/library/redis-trib:stable /bin/bash
root@redis-trib:/# 
root@redis-trib:/# redis-trib create --replicas 1 \
`dig +short redis-whx-0.redis-whx-service.default.svc.cluster.local`:6379 \
`dig +short redis-whx-1.redis-whx-service.default.svc.cluster.local`:6379 \
`dig +short redis-whx-2.redis-whx-service.default.svc.cluster.local`:6379 \
`dig +short redis-whx-3.redis-whx-service.default.svc.cluster.local`:6379 \
`dig +short redis-whx-4.redis-whx-service.default.svc.cluster.local`:6379 \
`dig +short redis-whx-5.redis-whx-service.default.svc.cluster.local`:6379
...
中间有一次询问，输入 yes 即可。
...
```

## 将redis集群导入cachecloud
1. 第一步：登录cachecloud WEB管理界面，依次进入后台管理-机器管理-添加新机器页面，这里，将每个容器理解为redis机器，因此共需要添加6台新机器，主机名依次为：  
    ```
    redis-whx-0.redis-whx-service.default.svc.cluster.local
    redis-whx-1.redis-whx-service.default.svc.cluster.local
    redis-whx-2.redis-whx-service.default.svc.cluster.local
    redis-whx-3.redis-whx-service.default.svc.cluster.local
    redis-whx-4.redis-whx-service.default.svc.cluster.local
    redis-whx-5.redis-whx-service.default.svc.cluster.local
    ```
    **说明**：主机名比较长，这就是需要修改cachecloud.sql中所有存储IP或者主机名字段长度的原因。
2. 第二步：导入redis集群
在cachecloud首页，点击右上角admin - 导入应用，依次输入应用名称、说明、负责人、报警阈值等，最重要的是输入实例详情，此处示例中，格式为：  
    ```
    redis-whx-0.redis-whx-service.default.svc.cluster.local:6379:8192
    redis-whx-1.redis-whx-service.default.svc.cluster.local:6379:8192
    redis-whx-2.redis-whx-service.default.svc.cluster.local:6379:8192
    redis-whx-3.redis-whx-service.default.svc.cluster.local:6379:8192
    redis-whx-4.redis-whx-service.default.svc.cluster.local:6379:8192
    redis-whx-5.redis-whx-service.default.svc.cluster.local:6379:8192
    ```
最后检查格式无误后，开始导入。 

3. 页面效果
