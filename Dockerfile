# Spesifiser ikke image SHA på chainguard - gå for latest hver gang
FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-26

ENV LC_ALL='nb_NO.UTF-8' LANG='nb_NO.UTF-8' TZ='Europe/Oslo'

USER nonroot

COPY build/libs/app*.jar app.jar
CMD ["app.jar"]