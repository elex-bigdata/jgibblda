echo $*
echo $@
args=$*
baseDir=`dirname $0`/..
JAR=$baseDir/target/jgibblda-jar-with-dependencies.jar
MAIN=com.elex.bigdata.jgibblda.LDA
echo "java -cp $JAR $MAIN -estc -dir /data/log/user_category/llda -ntopics 20 $args"
java -cp $JAR $MAIN -estc -dir /data/log/user_category/llda -ntopics 20 $args