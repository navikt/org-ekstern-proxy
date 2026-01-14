# org-ekstern-proxy
(Shameless ripoff fra https://github.com/navikt/saas-proxy)

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

## Oppsett for pre-commit trigger
Sett opp slik at pre-commit trigger kjøres lokalt på din maskin ved commit for å søke etter secrets, credentials og personinfo i endringer som sjekkes inn.

### Installering
Installer pre-commit på maskinen (trenger kun å kjøres én gang) (https://pre-commit.com/#install)
```shell
pip install pre-commit
```
Installer GitLeaks på maskinen (trenger kun å kjøres én gang) (https://github.com/gitleaks/gitleaks)
Eksempel:
```shell
brew install gitleaks
```
#### Installasjon med nix
```shell
nix profile add nixpkgs#pre-commit
nix profile add nixpkgs#gitleaks
```
### Verifiser installering
```shell
pre-commit --version
gitleaks version
```

### Aktivere pre-commit i prosjektet
Installer pre-commit hooks i github-prosjektet (trenger kun å kjøres én gang per prosjekt)
```shell
pre-commit install
```
Nå skal GitLeaks kjøre på alle endringer som forsøkes å commit'es. Finner den noe mistenkelig vil den stoppe commit'en og vise hva som er funnet.

Commit output skal vise noe slikt som dette:

    Detect hardcoded secrets using Gitleaks..................................Passed


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