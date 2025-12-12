# Spesifiser image SHA for alltid å kunne få dependabot til å si ifra om ny versjon. (For apper som ikke deployes ofte)
FROM gcr.io/distroless/java21-debian12@sha256:ed87b011df38601c55503cb24a0d136fed216aeb3bcd57925719488d93d236f4

ENV LC_ALL='nb_NO.UTF-8' LANG='nb_NO.UTF-8' TZ='Europe/Oslo'

USER nonroot

COPY build/libs/app*.jar app.jar
CMD ["app.jar"]