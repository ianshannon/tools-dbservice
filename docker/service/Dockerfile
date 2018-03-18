# create an image
# docker build -t security-service .

# Run the container
# docker run --rm -ti --net=host coresecurity
# docker run --rm -ti --net=host nyx.registry.dev.openbet.com/coresecurity
# or (without run as 'coresecurity' and don't remove)
# docker run --name coresecurity -ti --net=host nyx.registry.dev.openbet.com/coresecurity

# docker run --name coresecurity_1 -ti --net=host -p 9889:9001 nyx.registry.dev.openbet.com/coresecurity


FROM nyx.registry.dev.openbet.com/java8:1.0-SNAPSHOT

COPY docker-entrypoint /
COPY maven/target/*-bundle.jar /target/
COPY maven/target/classes/asciidoc/html/api.html /opt/openbet/security-service/api.html

RUN chmod +x /docker-entrypoint

EXPOSE 9815

ENTRYPOINT /docker-entrypoint $1 $2

