# JAR Builder
FROM amazoncorretto:21-alpine3.16 as builder
WORKDIR /app
COPY . /app
RUN find ./src -type f -name "*.java" -exec javac -classpath "./lib/*:./src" -d ./build '{}' +
RUN jar cvf ./ubercrawl.jar -C ./build .

# Define your base image
FROM amazoncorretto:21-alpine3.16
WORKDIR /app
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"
COPY --from=builder /app/ubercrawl.jar /java/lib/ubercrawl.jar
COPY --from=builder /app/lib /java/lib

