echo $*
echo $@
args=$*
baseDir=`dirname $0`/..
JAR=$baseDir/target/jgibblda-jar-with-dependencies.jar
MAIN=com.elex.bigdata.jgibblda.LDA
logFile=/data/log/user_category/processLog/llda/inf.log
echo "java -cp $JAR $MAIN -inf -dir /data/log/user_category/llda -ntopics 5 -model elex $args $args >> $logFile 2>&1"
java -cp $JAR $MAIN -inf -dir /data/log/user_category/llda -ntopics 5 -model elex $args >> $logFile 2>&1
