# Assistant Core

Backend di prodotto separato da `evolution-api`.

`assistant-core` gestisce la logica business del prodotto:

- onboarding tenant
- piani e subscription
- profili AI e prompt
- profili AI dinamici per tipi diversi di attivita
- segretaria appuntamenti
- dominio calendar e disponibilita
- supporto provider WhatsApp `evolution_baileys` e `whatsapp_business`
- automazioni e reminder
- orchestrazione verso `evolution-api`

`evolution-api` resta il layer di trasporto WhatsApp.

## Stack

- Java 21
- Spring Boot 3
- Maven
- PostgreSQL
- Flyway
- Spring Data JPA
- Spring Security
- Docker Compose

## Struttura attuale

Struttura MVC semplificata:

- `src/main/java/com/assistantcore/config`
- `src/main/java/com/assistantcore/controller`
- `src/main/java/com/assistantcore/dto`
- `src/main/java/com/assistantcore/model`
- `src/main/java/com/assistantcore/repository`
- `src/main/java/com/assistantcore/service`

## Funzionalita gia presenti

- `health check`
- onboarding mock persistito su PostgreSQL
- creazione `tenant`
- creazione `plan`
- creazione `subscription`
- creazione `channel_instance`
- client HTTP verso `evolution-api`
- creazione automatica istanza WhatsApp su Evolution
- profili AI dinamici
- preset AI per settori diversi
- assegnazione profilo AI a `channel_instance`

## Database

Le migration attuali creano e aggiornano queste tabelle:

- `tenants`
- `plans`
- `subscriptions`
- `channel_instances`
- `ai_profiles`
- `contacts`
- `conversations`
- `messages`
- `calendar_connections`
- `oauth_states`
- `google_calendar_credentials`
- `working_hours`
- `appointment_types`

File migration:

- [V1__init.sql](./src/main/resources/db/migration/V1__init.sql)
- [V2__ai_profiles_dynamic.sql](./src/main/resources/db/migration/V2__ai_profiles_dynamic.sql)
- [V3__tenant_business_context.sql](./src/main/resources/db/migration/V3__tenant_business_context.sql)
- [V4__contacts_conversations_messages.sql](./src/main/resources/db/migration/V4__contacts_conversations_messages.sql)
- [V5__calendar_availability_domain.sql](./src/main/resources/db/migration/V5__calendar_availability_domain.sql)
- [V6__google_oauth_states.sql](./src/main/resources/db/migration/V6__google_oauth_states.sql)
- [V7__google_calendar_credentials.sql](./src/main/resources/db/migration/V7__google_calendar_credentials.sql)
- [V8__channel_provider_support.sql](./src/main/resources/db/migration/V8__channel_provider_support.sql)

## Avvio con Docker

Da questa cartella:

```powershell
docker compose up -d --build
```

Servizi esposti:

- `assistant-core`: `http://127.0.0.1:8090`
- `postgres`: `localhost:5433`

Il compose usa:

- database: `assistant_core`
- user: `postgres`
- password: `postgres`

## Configurazione

Variabili principali:

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SERVER_PORT`
- `EVOLUTION_BASE_URL`
- `EVOLUTION_API_KEY`
- `GOOGLE_OAUTH_CLIENT_ID`
- `GOOGLE_OAUTH_CLIENT_SECRET`
- `GOOGLE_OAUTH_REDIRECT_URI`
- `GOOGLE_OAUTH_TOKEN_ENCRYPTION_SECRET`
- `WHATSAPP_BUSINESS_ACCESS_TOKEN`
- `APP_BASIC_AUTH_USERNAME`
- `APP_BASIC_AUTH_PASSWORD`

Valori di default importanti:

- `EVOLUTION_BASE_URL=http://host.docker.internal:8080`
- `EVOLUTION_API_KEY=change_me_local_api_key`
- `APP_BASIC_AUTH_USERNAME=assistant_admin`
- `APP_BASIC_AUTH_PASSWORD=change_me_assistant_password`

Se `evolution-api` gira in Docker o su un host diverso, aggiorna `EVOLUTION_BASE_URL`.

Le credenziali Basic Auth sopra sono solo per sviluppo locale e vanno cambiate prima di usare il servizio fuori da test interni.

## Endpoint disponibili

Pubblici:

- `GET /api/v1/health`
- `POST /api/v1/webhooks/evolution/messages`
- `GET /api/v1/oauth/google/calendar/callback`

Protetti con Basic Auth:

- `GET /api/v1/ai-profiles/presets`
- `GET /api/v1/ai-profiles/tenant/{tenantId}`
- `POST /api/v1/ai-profiles/tenant/{tenantId}`
- `POST /api/v1/ai-profiles/tenant/{tenantId}/from-preset`
- `POST /api/v1/ai-profiles/tenant/{tenantId}/appointment-secretary/default`
- `POST /api/v1/ai-profiles/channel-instances/{channelInstanceId}/assign/{profileId}`
- `GET /api/v1/calendar-connections/tenant/{tenantId}`
- `POST /api/v1/calendar-connections/tenant/{tenantId}/google/manual`
- `PUT /api/v1/calendar-connections/{connectionId}/working-hours`
- `PUT /api/v1/calendar-connections/{connectionId}/appointment-types`
- `GET /api/v1/oauth/google/calendar/start/{connectionId}`
- `GET /api/v1/oauth/google/calendar/available-calendars/{connectionId}`
- `POST /api/v1/onboarding/mock`
- `POST /api/v1/app-users/sync`
- `GET /api/v1/app-users/{supabaseUserId}/memberships`
- `GET /api/v1/app-users/{supabaseUserId}/workspace`

Rotte sensibili protette anche da contesto utente applicativo:

- oltre alla Basic Auth, queste rotte si aspettano anche l'header `X-App-User-Id`
- il valore deve essere il `supabaseUserId` dell'utente applicativo autenticato
- `assistant-core` usa questo header per ripetere i controlli di ownership su app user, tenant, channel instance e calendar connection
- `ai-secretary-web` imposta questo header automaticamente nel BFF; client diretti devono valorizzarlo in modo coerente

Credenziali di default per i test:

- username: `assistant_admin`
- password: `change_me_assistant_password`

## AI profiles dinamici

Un `AI profile` definisce il comportamento dell'assistente:

- nome e slug
- tipo profilo
- tipo attivita
- lingua
- modello
- prompt di sistema
- messaggio iniziale
- tools disponibili
- config JSON
- flag `default` e `active`

Preset attuali:

- `appointment_secretary_clinic`
- `appointment_secretary_dental`
- `appointment_secretary_aesthetics`
- `sales_assistant_real_estate`
- `customer_support_retail`

Flusso consigliato:

1. crea il tenant con onboarding mock
2. crea un profilo AI da preset o custom
3. assegna il profilo al `channel_instance`
4. usa quel profilo per orchestrare i messaggi WhatsApp

## Calendar e disponibilita

Il backend ora ha un primo dominio agenda separato da Google OAuth:

- `calendar_connections`
- `working_hours`
- `appointment_types`

Questo serve a:

- modellare il calendario che il tenant usera
- definire orari di lavoro
- definire durata e buffer dei servizi
- passare al runtime AI un contesto coerente per richieste di disponibilita

Il webhook rileva gia intenti come:

- `check_availability`
- `create_appointment`
- `reschedule_appointment`
- `cancel_appointment`
- `pricing_question`

e arricchisce il prompt con:

- stato del calendario
- orari di lavoro
- tipi di appuntamento
- periodo richiesto dal cliente, se rilevato

Per ora non c'e ancora lettura live degli eventi Google Calendar.
Quindi l'assistente puo gia comportarsi meglio come segretaria,
ma non deve confermare disponibilita reale finche non colleghiamo Google OAuth e la lettura eventi.

## Google OAuth calendario

E adesso presente un flusso OAuth Google isolato dal record di configurazione agenda:

1. crea una `calendar_connection`
2. chiama `GET /api/v1/oauth/google/calendar/start/{connectionId}`
3. apri `authorizationUrl`
4. Google richiama `GET /api/v1/oauth/google/calendar/callback`
5. il backend salva i token cifrati in `google_calendar_credentials`
6. il backend aggiorna `calendar_connections` solo con metadati funzionali come email, calendario selezionato e stato
7. puoi leggere i calendari disponibili con `GET /api/v1/oauth/google/calendar/available-calendars/{connectionId}`

Per usarlo davvero devi configurare:

- `GOOGLE_OAUTH_CLIENT_ID`
- `GOOGLE_OAUTH_CLIENT_SECRET`
- `GOOGLE_OAUTH_REDIRECT_URI`
- `GOOGLE_OAUTH_TOKEN_ENCRYPTION_SECRET`

Il redirect URI deve coincidere esattamente con quello registrato nella Google Cloud Console.

Note di sicurezza:

- i token Google non devono piu vivere nel record `calendar_connections`
- `GOOGLE_OAUTH_TOKEN_ENCRYPTION_SECRET` deve essere lungo, casuale e diverso per ogni ambiente
- il callback OAuth e pubblico per necessita del provider, ma lo `state` resta monouso e con scadenza

## Provider WhatsApp

`assistant-core` ora seleziona un gateway in base a `channel_instances.provider_type`.

Provider supportati:

- `evolution_baileys`
- `whatsapp_business`

Comportamento:

- `evolution_baileys` usa pairing QR/code e sessioni locali
- `whatsapp_business` usa l'integrazione `WHATSAPP-BUSINESS` di `evolution-api`
- l'orchestrazione messaggi e l'outbound non chiamano piu direttamente un solo client hardcoded

Ottimizzazione risorse:

- le istanze Baileys create da `assistant-core` partono con:
  - `alwaysOnline=false`
  - `readMessages=false`
  - `readStatus=false`
  - `syncFullHistory=false`
- questo riduce presenza online inutile, sync storico e carico sessione
- i canali `whatsapp_business` non usano pairing locale e consumano molte meno risorse runtime

Nota pratica:

- usa `evolution_baileys` per onboarding rapido e linked device
- usa `whatsapp_business` per stabilita e scaling
- per `whatsapp_business` devi configurare `WHATSAPP_BUSINESS_ACCESS_TOKEN` e valorizzare `provider_external_id` con il `phone_number_id` Meta

## Checklist staging deploy

Obiettivo: mettere online uno staging sicuro abbastanza per test reali, non ancora una produzione aperta a clienti finali.

### 1. Frontend su Vercel

- collega il repo `ai-secretary-web` a Vercel
- imposta `ASSISTANT_CORE_BASE_URL`
- imposta `ASSISTANT_CORE_BASIC_USER`
- imposta `ASSISTANT_CORE_BASIC_PASSWORD`
- usa dominio preview o staging dedicato

### 2. Database su Supabase

- crea progetto Postgres dedicato
- prendi connection string diretta o pooler
- abilita SSL
- configura backup e retention
- imposta `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` in `assistant-core`

### 3. Backend runtime fuori da Vercel

- deploya `assistant-core` su VPS, Railway, Render o Fly
- deploya `evolution-api` su VPS o host equivalente
- non deployare `assistant-core` o `evolution-api` su Vercel
- esponi `assistant-core` dietro HTTPS
- limita IP o proteggi gli endpoint admin appena introduci auth tenant-based

### 4. Variabili obbligatorie di staging

- `EVOLUTION_BASE_URL`
- `EVOLUTION_API_KEY`
- `OPENAI_BASE_URL`
- `OPENAI_API_KEY`
- `OPENAI_MODEL`
- `GOOGLE_OAUTH_CLIENT_ID`
- `GOOGLE_OAUTH_CLIENT_SECRET`
- `GOOGLE_OAUTH_REDIRECT_URI`
- `GOOGLE_OAUTH_TOKEN_ENCRYPTION_SECRET`
- `WHATSAPP_BUSINESS_ACCESS_TOKEN`
- `APP_BASIC_AUTH_USERNAME`
- `APP_BASIC_AUTH_PASSWORD`

### 5. Hardening minimo prima di staging pubblico

- aggiungere firma o secret ai webhook Evolution
- evitare Basic Auth globale condivisa per tenant diversi
- ruotare le password default di sviluppo
- mantenere il controllo `X-App-User-Id` solo dietro servizi trusted come il BFF

### 6. Cose che puoi gia validare in staging

- onboarding frontend
- pairing WhatsApp
- webhook inbound/outbound
- profili AI
- collegamento Google Calendar
- persistenza conversazioni

### 7. Cose da non considerare ancora production-ready

- multi-tenant auth completa
- isolamento per utente del flow Google OAuth
- firma webhook forte
- refresh token Google automatico
- segregazione completa tra moduli onboarding, WhatsApp e calendar

## Esempio onboarding mock

Richiesta:

```json
{
  "businessName": "Studio Dentistico Bianchi",
  "phoneNumber": "393513845222",
  "ownerName": "Luca Bianchi",
  "planCode": "starter",
  "timezone": "Europe/Rome",
  "autoCreateInstance": true,
  "autoConnect": false
}
```

PowerShell:

```powershell
$pair = 'assistant_admin:change_me_assistant_password'
$bytes = [System.Text.Encoding]::ASCII.GetBytes($pair)
$token = [Convert]::ToBase64String($bytes)

$body = @{
  businessName = "Studio Dentistico Bianchi"
  phoneNumber = "393513845222"
  ownerName = "Luca Bianchi"
  planCode = "starter"
  timezone = "Europe/Rome"
  autoCreateInstance = $true
  autoConnect = $false
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8090/api/v1/onboarding/mock" `
  -Headers @{
    Authorization = "Basic $token"
    "X-App-User-Id" = "supabase-user-id-demo"
  } `
  -ContentType "application/json" `
  -Body $body | ConvertTo-Json -Depth 8
```

Effetto attuale:

- salva tenant e subscription nel DB
- crea `channel_instance`
- crea l'istanza corrispondente in `evolution-api` se `autoCreateInstance=true`
- richiede il connect su Evolution se `autoConnect=true`

## Esempio AI profile da preset

```powershell
$pair = 'assistant_admin:change_me_assistant_password'
$bytes = [System.Text.Encoding]::ASCII.GetBytes($pair)
$token = [Convert]::ToBase64String($bytes)

$body = @{
  presetKey = "appointment_secretary_dental"
  name = "Secretaria Odontologica Premium"
  slug = "odonto-premium"
  isDefault = $true
  active = $true
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://127.0.0.1:8090/api/v1/ai-profiles/tenant/TENANT_ID/from-preset" `
  -Headers @{
    Authorization = "Basic $token"
    "X-App-User-Id" = "supabase-user-id-demo"
    "Content-Type" = "application/json"
  } `
  -Body $body | ConvertTo-Json -Depth 8
```

## Flusso attuale

1. `assistant-core` riceve l'onboarding mock
2. salva i dati nel proprio database
3. crea l'istanza WhatsApp su `evolution-api`
4. crea o assegna un profilo AI al canale
5. opzionalmente richiede il pairing/connect
6. restituisce gli ID applicativi e lo stato del canale

## Prossimi passi naturali

- collegare il webhook messaggi di `evolution-api`
- usare il `channel_instance.ai_profile_id` per l'orchestrazione
- salvare stato pairing/QR nel dominio applicativo
- introdurre appuntamenti e calendar connector
- sostituire il mock onboarding con signup/subscription reali
