args=$*
baseDir=`dirname $0`/..
JAR=$baseDir/target/jgibblda-jar-with-dependencies.jar
MAIN=com.elex.bigdata.jgibblda.ResultEtl
logFile=/data/user_category/llda/logs/etl.log
echo "java -cp $JAR $MAIN -modelDir /data/user_category/llda/models $args "
echo "java -cp $JAR $MAIN -modelDir /data/user_category/llda/models $args " >> $logFile
java -cp $JAR $MAIN -modelDir /data/user_category/llda/models $args
