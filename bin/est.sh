echo $*
echo $@
args=$*
baseDir=`dirname $0`/..
JAR=$baseDir/target/jgibblda-jar-with-dependencies.jar
MAIN=com.elex.bigdata.jgibblda.LDA
logFile=/data/user_category/llda/logs/est.log
echo "java -cp $JAR $MAIN -est -modelDir /data/user_category/llda/models -docDir /data/user_category/llda/docs -model elex -ntopics 3 $args >> $logFile 2>&1"
echo "java -cp $JAR $MAIN -est -modelDir /data/user_category/llda/models -docDir /data/user_category/llda/docs -model elex -ntopics 3 $args >> $logFile 2>&1" >> $logFile
java -cp $JAR $MAIN -est -modelDir /data/user_category/llda/models -docDir /data/user_category/llda/docs -model elex -ntopics 3 $args >> $logFile 2>&1
