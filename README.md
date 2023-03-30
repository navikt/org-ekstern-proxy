# org-ekstern-proxy
API for tjenester utenfor Nais klustere for å nå interne nav-apier i google cloud levert av team org.
Proxyen slipper kun gjennom hvitelistede anrop med et gyldig azure token.

Du må legge til inbound rule i den app du vil eksponere fra:
```
- application: org-ekstern-proxy
  namespace: org
```
Samt outbound rule her i [dev.yml](https://github.com/navikt/org-ekstern-proxy/blob/master/.nais/dev.yaml) og [prod.yml](https://github.com/navikt/org-ekstern-proxy/blob/master/.nais/prod.yaml)::
```
- application: <app>
  namespace: <namespace>
```

Du legger til de endepunkter du vil gjøre tilgjengelig i hvitelisten før hvert miljø. Se
[dev.json](https://github.com/navikt/org-ekstern-proxy/blob/master/src/main/resources/whitelist/dev.json)
og
[prod.json](https://github.com/navikt/org-ekstern-proxy/blob/master/src/main/resources/whitelist/dev.json)

Hvitelisten er strukturert under *"namespace"* *"app"* *"pattern"*, der *"pattern"* er en streng bestående av http-metoden og regulære uttrykk før path, f.eks:
```
"GET /getcall",
"POST /done",
"GET /api/.*"
```

### Test aktive hvitlisteregler
Du kan teste om ett anrop er bestått eller ikke mot aktive regler hvis du går imot (eks med postman)

https://org-ekstern-proxy.dev.intern.nav.no/internal/test/<uri-du-vil-testa>

https://org-ekstern-proxy.intern.nav.no/internal/test/<uri-du-vil-testa>

med header **target-app** med appen du ønsker nå.

### Bruk av proxyn

De eksterna klientene som ønsker anrope via proxyen må sende med tre headers:

**target-app** - den app de ønsker nå (ex. nom-api)

**target-client-id** - azure client id før appen

**Authorization** - azure token

De bruker samme metode og uri som om de skulle anrope en ingress till den interne appen, men ingressen til proxyn (dev: https://org-ekstern-proxy.ekstern.dev.nav.no, prod: https://org-ekstern-proxy.nav.no)

Eks:


```
https://nom-api.dev.intern.nav.no/do/a/call?param=1
```
blir
```
https://org-ekstern-proxy.ekstern.dev.nav.no/do/a/call?param=1
```
NB Appen trenger ikke ha en ingress for å være tilgjengelig via proxy