#!/usr/bin/env bash

em="\"\""
cp=""
if [ $em != $2 ]; then
    cp = $2
fi

if [ $OMPI_COMM_WORLD_RANK = "0" ]; then
    echo "Rank 0"
fi

java -Xmx2048m -Xms2048m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -Djava.util.logging.config.file=nodesmpi/logger.properties $cp -cp $3 edu.iu.dsc.tws.rsched.schedulers.mpi.MPIProcess --container_class $4 --job_name $5 --twister2_home $6 --cluster_type nodesmpi --config_dir $7 &