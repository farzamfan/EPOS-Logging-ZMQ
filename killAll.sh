PID=`ps -ef | grep IEPOSnode | awk '{ print $2 }'`; kill -9 $PID;
PID=`ps -ef | grep GateWay | awk '{ print $2 }'`; kill -9 $PID;
PID=`ps -ef | grep IEPOSUsers | awk '{ print $2 }'`; kill -9 $PID;
screen -wipe;
