FROM openjdk:8-jre-stretch

ARG VERSION=1.6.5

ENV WEBAPPS_DIR /boot/webapps
ENV RESOURCES_DIR /boot/resources
ENV GC_LOGS_DIR /gclogs

RUN set -xe \
    && mkdir -p "$WEBAPPS_DIR" \
    && mkdir -p "$GC_LOGS_DIR"

ENV JVM_MEMORY "-Xms1G -Xmx1G -XX:+UseConcMarkSweepGC -XX:+DisableExplicitGC -Xloggc:$GC_LOGS_DIR/gc.out -verbose:gc -XX:+UseGCLogFileRotation -XX:NumberOfGCLogFiles=3 -XX:GCLogFileSize=20M -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintTenuringDistribution"
ENV JAVA_OPTIONS "$JAVA_OPTIONS $JVM_MEMORY -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

COPY deployment/docker/entrypoint.sh /tmp/entrypoint.sh
COPY build/libs/hydra.war $WEBAPPS_DIR/

RUN set -xe \
    && chmod +x /tmp/entrypoint.sh

ENTRYPOINT ["/tmp/entrypoint.sh"]

CMD ["sh", "-c", "java $JAVA_OPTIONS -cp $RESOURCES_DIR -jar $WEBAPPS_DIR/hydra.war --spring.config.location=classpath:/application.yml"]

LABEL org.label-schema.schema-version=1.0 \
      org.label-schema.version=$VERSION
