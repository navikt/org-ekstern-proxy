# Spesifiser image SHA for alltid å kunne få dependabot til å si ifra om ny versjon. (For apper som ikke deployes ofte)
FROM gcr.io/distroless/java21-debian12@sha256:3525cafa2114ed879d3554acbbd3ba1b388e3cd3e8833ff58713ba133b0e1173

ENV LC_ALL='nb_NO.UTF-8' LANG='nb_NO.UTF-8' TZ='Europe/Oslo'

USER nonroot

COPY build/libs/app*.jar app.jar
CMD ["app.jar"]