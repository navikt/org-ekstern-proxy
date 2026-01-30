# Spesifiser image SHA for alltid å kunne få dependabot til å si ifra om ny versjon. (For apper som ikke deployes ofte)
FROM gcr.io/distroless/java21-debian12@sha256:04f730658c79b99d42fadf2f8dd11c9d441cee10ce5eaa7cad4a75eaca2cb52a

ENV LC_ALL='nb_NO.UTF-8' LANG='nb_NO.UTF-8' TZ='Europe/Oslo'

USER nonroot

COPY build/libs/app*.jar app.jar
CMD ["app.jar"]