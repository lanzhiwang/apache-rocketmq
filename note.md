
```bash
git checkout -b example-release-4.9.3 remotes/origin/release-4.9.3

docker run -it --rm --name my-maven-project \
-v ~/work/code/go_code/rocketmq/rocketmq:/usr/src/mymaven \
-v ~/.m2:/root/.m2 \
-w /usr/src/mymaven \
--network host \
maven:3.9.6-eclipse-temurin-8-focal bash


find example/src/main/java/org/apache/rocketmq/example -name "*.java" -exec /usr/local/opt/openjdk@11/bin/java -jar format/google-java-format-1.20.0-all-deps.jar -a -i {} \;

mvn -Prelease-all -DskipTests -Dspotbugs.skip=true -s /usr/src/mymaven/settings.xml clean install -U -X

java -jar target/rocketmq-example-4.9.4-SNAPSHOT.jar

```