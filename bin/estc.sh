echo $*
echo $@
args=$*
baseDir=`dirname $0`/..
JAR=$baseDir/target/jgibblda-jar-with-dependencies.jar
MAIN=com.elex.bigdata.jgibblda.LDA
logFile=/data/user_category/llda/logs/estc.log
echo "java -cp $JAR $MAIN -estc -modelDir /data/user_category/llda/models -docDir /data/user_category/llda/docs -model elex -ntopics 5 $args >> $logFile 2>&1"
java -cp $JAR $MAIN -estc -modelDir /data/user_category/llda/models -docDir /data/user_category/llda/docs -model elex -ntopics 5 $args >> $logFile 2>&1
