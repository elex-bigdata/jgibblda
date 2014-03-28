echo $*
echo $@
args=$*
baseDir=`dirname $0`/..
JAR=$baseDir/target/jgibblda-jar-with-dependencies.jar
MAIN=com.elex.bigdata.jgibblda.LDA
logFile=/data/log/user_category/processLog/llda/est.log
echo "java -cp $JAR $MAIN -est -dir /data/log/user_category/llda  -model elex -ntopics 5 $args >> $logFile 2>&1"
java -cp $JAR $MAIN -est -dir /data/log/user_category/llda  -model elex -ntopics 5 $args >> $logFile 2>&1
