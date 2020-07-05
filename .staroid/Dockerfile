FROM ubuntu:18.04 AS builder

# install jdk
RUN apt-get -y update && \
    apt-get install -y wget openjdk-8-jdk && \
    rm -rf /var/lib/apt/lists/*

# install maven
RUN wget -q https://www-us.apache.org/dist/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz -P /tmp/ && \
    tar xf /tmp/apache-maven-*.tar.gz -C /opt
ENV PATH="/opt/apache-maven-3.6.3/bin:${PATH}"
ENV MAVEN_CLI_OPTS="--no-transfer-progress"

# build jar
WORKDIR /camelympics
COPY ./ /camelympics/
RUN mvn compile assembly:single


# container image
FROM ubuntu:18.04

RUN apt-get -y update && \
    apt-get install -y curl openjdk-8-jre-headless && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /camelympics
COPY --from=builder /camelympics/target/camelympics.jar /camelympics/

# set non-root user
USER 1000
