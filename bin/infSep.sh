echo $*
echo $@
args=$*
baseDir=`dirname $0`/..
JAR=$baseDir/target/jgibblda-jar-with-dependencies.jar
MAIN=com.elex.bigdata.jgibblda.LDA
logFile=/data/log/user_category/processLog/llda/infSep.log
echo "java -cp $JAR $MAIN -infseparately -dir /data/log/user_category/llda/labeledDocs -ntopics 5 -model elex $args >> $logFile 2>&1"
java -cp $JAR $MAIN -infseparately -dir /data/log/user_category/llda/labeledDocs/ -ntopics 5 -model elex $args  >> $logFile 2>&1
