# Spesifiser image SHA for alltid å kunne få dependabot til å si ifra om ny versjon. (For apper som ikke deployes ofte)
FROM gcr.io/distroless/java21-debian12@sha256:320d27b74347b6baaf35bcbe21bae51f738b07ed2c0741ead5cf050a3b5c3487

ENV LC_ALL='nb_NO.UTF-8' LANG='nb_NO.UTF-8' TZ='Europe/Oslo'

USER nonroot

COPY build/libs/app*.jar app.jar
CMD ["app.jar"]