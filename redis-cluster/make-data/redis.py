#!/usr/bin/env python
# coding=utf-8
# Jack Kang
from rediscluster import StrictRedisCluster
import sys
import time

def redis_cluster():
    redis_nodes = [{'host':'10.244.3.45','port':6379},
                   {'host':'10.244.0.116','port':6379},
                   {'host':'10.244.2.82','port':6379},
                   {'host':'10.244.1.15','port':6379},
                   {'host':'10.244.3.46','port':6379},
                   {'host':'10.244.2.83','port':6379},
                  ]
    try:
        redisconn = StrictRedisCluster(startup_nodes = redis_nodes)
    except Exception,e:
        print "connect error"
        sys.exit(1)
    #k = range(1,100000000000000000000)
    #a = {}
    #btime = int(time.time())
    #print btime
    #for i in range(1,100000000000000000000):
        #if i < len(k):
    i = 20309861
    while 1:
        redisconn.set("asdfjaskdjfaksjdhfklasjh" + str(i),"asdfaskldjflkajshnvcklajsdfglkjasdflkas" + str(i))
        key = "asdfjaskdjfaksjdhfklasjh" + str(i)
        value = redisconn.get(key)
        #print "%s is " % key, redisconn.get(key)
        i += 1
            #redisconn_pipe.get(k[i])
            #redisconn_pipe.execute()
    #atime = int(time.time())
    #print atime
    #print atime - btime
if __name__ == "__main__":
    redis_cluster()
