# Spesifiser image SHA for alltid å kunne få dependabot til å si ifra om ny versjon. (For apper som ikke deployes ofte)
FROM gcr.io/distroless/java21-debian12@sha256:c04d060a6b212457673a4461fa026b82681c658cbed95c6b6c8a342bb175d323

ENV LC_ALL='nb_NO.UTF-8' LANG='nb_NO.UTF-8' TZ='Europe/Oslo'
ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml"

USER nonroot

COPY build/libs/app*.jar app.jar
CMD ["app.jar"]