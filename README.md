# Assistant Core

Backend di prodotto separato da `evolution-api`.

`assistant-core` gestisce la logica business del prodotto:

- onboarding tenant
- piani e subscription
- profili AI e prompt
- profili AI dinamici per tipi diversi di attivita
- segretaria appuntamenti
- dominio calendar e disponibilita
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
- `working_hours`
- `appointment_types`

File migration:

- [V1__init.sql](./src/main/resources/db/migration/V1__init.sql)
- [V2__ai_profiles_dynamic.sql](./src/main/resources/db/migration/V2__ai_profiles_dynamic.sql)
- [V3__tenant_business_context.sql](./src/main/resources/db/migration/V3__tenant_business_context.sql)
- [V4__contacts_conversations_messages.sql](./src/main/resources/db/migration/V4__contacts_conversations_messages.sql)
- [V5__calendar_availability_domain.sql](./src/main/resources/db/migration/V5__calendar_availability_domain.sql)

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
- `POST /api/v1/onboarding/mock`
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

E adesso presente il primo flusso OAuth Google lato backend:

1. crea una `calendar_connection`
2. chiama `GET /api/v1/oauth/google/calendar/start/{connectionId}`
3. apri `authorizationUrl`
4. Google richiama `GET /api/v1/oauth/google/calendar/callback`
5. il backend salva token e prova a collegare il calendario selezionato
6. puoi leggere i calendari disponibili con `GET /api/v1/oauth/google/calendar/available-calendars/{connectionId}`

Per usarlo davvero devi configurare:

- `GOOGLE_OAUTH_CLIENT_ID`
- `GOOGLE_OAUTH_CLIENT_SECRET`
- `GOOGLE_OAUTH_REDIRECT_URI`

Il redirect URI deve coincidere esattamente con quello registrato nella Google Cloud Console.

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
