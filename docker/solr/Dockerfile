FROM ubuntu:trusty

EXPOSE 8983

RUN apt-get update && \
  DEBIAN_FRONTEND=noninteractive apt-get install -y \
    openjdk-7-jdk curl make git php5-cli php5-json 

ENV PGHOST localhost
ENV PGPORT 6000
ENV PGDATABASE transmart
ENV PGUSER postgres
ENV PGPASSWORD '' 
ENV TABLESPACES bogus
ENV BRANCH projects/VEEN

RUN git clone -b $BRANCH --depth 1 https://github.com/thehyve/transmart-data.git /opt/transmart-data && \
  make -C /opt/transmart-data/solr solr_home && \
  curl -f -L -o /usr/local/bin/dumb-init https://github.com/Yelp/dumb-init/releases/download/v1.0.1/dumb-init_1.0.1_amd64 && \
  chmod +x /usr/local/bin/dumb-init

CMD ["/usr/local/bin/dumb-init", "/usr/bin/make", "-C", "/opt/transmart-data/solr", "start"]
