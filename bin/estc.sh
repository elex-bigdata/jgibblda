echo $*
echo $@
args=$*
baseDir=`dirname $0`/..
JAR=$baseDir/target/jgibblda-jar-with-dependencies.jar
MAIN=com.elex.bigdata.jgibblda.LDA
logFile=/data/log/user_category/processLog/llda/estc.log
echo "java -cp $JAR $MAIN -estc -dir /data/log/user_category/llda/ -ntopics 5 -model elex $args >> $logFile 2>&1"
java -cp $JAR $MAIN -estc -dir /data/log/user_category/llda -ntopics 5 -model elex $args  >> $logFile 2>&1
