# Spesifiser image SHA for alltid å kunne få dependabot til å si ifra om ny versjon. (For apper som ikke deployes ofte)
FROM gcr.io/distroless/java21-debian12@sha256:d8f16c5beb203e0890e6477706912e725d55c01a3ff3fe03e744f4adb0be3335

ENV LC_ALL='nb_NO.UTF-8' LANG='nb_NO.UTF-8' TZ='Europe/Oslo'

USER nonroot

COPY build/libs/app*.jar app.jar
CMD ["app.jar"]