echo $*
echo $@
args=$*
baseDir=`dirname $0`/..
JAR=$baseDir/target/jgibblda-jar-with-dependencies.jar
MAIN=com.elex.bigdata.jgibblda.LDA
logFile=/data/user_category/llda/logs/inf.log
echo "java -cp $JAR $MAIN -inf -modelDir /data/user_category/llda/models -docDir /data/user_category/llda/docs -ntopics 3 -model elex  $args >> $logFile 2>&1"
java -cp $JAR $MAIN -inf -modelDir /data/user_category/llda/models -docDir /data/user_category/llda/docs -ntopics 3 -model elex  $args >> $logFile 2>&1
