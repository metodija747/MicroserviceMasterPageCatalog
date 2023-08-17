FROM adoptopenjdk:11-jdk-hotspot

# Create a directory for logs and set permissions
RUN mkdir -p /app/logs && chown -R 1000:1000 /app/logs

# Set up the volume for logs
VOLUME /app/logs

# Your existing setup
VOLUME /tmp
COPY target/master.microservice-catalog-1.0.0.jar app.jar
ENTRYPOINT ["java","-jar","/app.jar"]
