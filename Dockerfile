# Spesifiser image SHA for alltid å kunne få dependabot til å si ifra om ny versjon. (For apper som ikke deployes ofte)
FROM gcr.io/distroless/java21-debian12@sha256:7c9a9a362eadadb308d29b9c7fec2b39e5d5aa21d58837176a2cca50bdd06609

ENV LC_ALL='nb_NO.UTF-8' LANG='nb_NO.UTF-8' TZ='Europe/Oslo'
ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml"

USER nonroot

COPY build/libs/app*.jar app.jar
CMD ["app.jar"]