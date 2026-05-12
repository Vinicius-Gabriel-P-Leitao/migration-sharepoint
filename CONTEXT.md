# SP Migrator — Contexto do Projeto

## Spec Original

Aplicação web para migração de dados do SharePoint (via Microsoft Graph API) para bancos de dados relacionais e NoSQL de forma dinâmica, com agendamento de jobs via Quartz e interface web para gerenciamento.

**Stack planejada**
- Java 25, Spring Boot 4.0.6, Gradle Kotlin DSL
- Quartz Scheduler (RAMJobStore, recarga do SQLite no boot)
- Microsoft Graph SDK + Bucket4J (rate limit)
- SQLite + Spring Data JPA + Hibernate
- Lombok, Validation, SpringDoc/Swagger, Actuator
- Drivers: PostgreSQL, MySQL, MongoDB
- Frontend: Vite + React + shadcn/ui, servido como estático pelo Spring
- Deploy: Docker único container

---

## Status de Implementação

### ✅ Implementado

**Domínio / Persistência**
- `MigrationJob` — entidade JPA com: siteId, listId, fieldMappings (Map→JSON), targetDb, connectionString, tableName, scheduleType, intervalValue/Unit, cronExpression, timestamps
- `MigrationLog` — entidade JPA com: job (FK), status, startedAt, finishedAt, errorMessage
- `MigrationJobRepository` / `MigrationLogRepository` — Spring Data JPA
- `MapToJsonConverter` — JPA converter para `Map<String,String>` ↔ JSON TEXT

**Enums**
- `JobStatus` — RUNNING, SUCCESS, FAILED
- `ScheduleType` — MANUAL, INTERVAL, CRON, CONTINUOUS
- `TargetDb` — MYSQL _(apenas MySQL, ver gaps abaixo)_
- `IntervalUnit` — MINUTES, HOURS, DAYS

**API REST** (`/v1/jobs`)
- `GET /v1/jobs` — lista todos os jobs
- `GET /v1/jobs/{id}` — busca por ID
- `POST /v1/jobs` — cria e agenda
- `PUT /v1/jobs/{id}` — atualiza e reagenda
- `DELETE /v1/jobs/{id}` — remove e desagenda
- `POST /v1/jobs/{id}/run` — disparo manual (202)
- `GET /v1/jobs/{id}/logs` — histórico de execuções

**DTOs** (Java Records)
- `JobRequest` — request com validações `@NotBlank` / `@NotNull`
- `JobResponse` — resposta enriquecida com id e timestamps
- `LogResponse` — resposta de log com jobId, status, datas, erro

**Execução do Job (Quartz)**
- `SharePointMigrationJob` — `@DisallowConcurrentExecution`, fluxo: Log(RUNNING) → GraphClient → fieldMapping → Writer → Log(SUCCESS/FAILED)
- `QuartzSchedulerService` — suporte a MANUAL, INTERVAL, CRON, CONTINUOUS (self-triggering)
- `JobReloadStartupRunner` — reagenda todos os jobs do SQLite no boot

**Integração Microsoft Graph**
- `GraphClient` — autenticação Bearer token, paginação via `@odata.nextLink`, retorna `List<Map<String,Object>>`

**Writer de banco**
- `MySqlMigrationWriter` — JDBC direto, valida tableName (regex), verifica existência de tabela e colunas, batch insert configurável (default 500), transação com rollback

**Infraestrutura**
- `ServerSecurityConfig` — stateless, GETs públicos, Swagger público, headers HSTS/CSP/COOP/CORP
- `RateLimitingFilter` — Bucket4j, 100 req/min por IP, 429 JSON em /v1/* e redirect em /
- `CsrfCookieFilter` — CSRF cookie
- `HttpExceptionHandler` — `@RestControllerAdvice` cobrindo ~10 tipos de exceção
- `CustomErrorController` — erros não mapeados retornam JSON estruturado
- `SpaForwardController` — forward de rotas sem extensão para index.html
- `ApplicationConfig` — ObjectMapper (JavaTimeModule, timezone SP), BCrypt
- `OpenApiConfig` — Swagger com Bearer security scheme
- `RequestUtil` — extração de IP real (X-Forwarded-For)
- `AppException` + exceções customizadas (BadRequest, Conflict, Infrastructure, NotFound) + `ErrorCode` enum

**Configuração**
- `application.properties` — porta, SQLite, Quartz, Graph API, logging MDC, rate limit
- `libs.versions.toml` — versões centralizadas
- `mise.toml` — Java 25 pinado

---

### ❌ Não implementado / Gaps

| Item | Detalhe |
|------|---------|
| `TomcatConfig.java` | Classe criada mas vazia — sem implementação |
| `TargetDb` incompleto | Enum tem só `MYSQL`. `PostgreSQL` e `MongoDB` estão como drivers no build mas sem writer |
| Writers faltando | `PostgreSqlMigrationWriter` e `MongoMigrationWriter` não existem |
| Credenciais Azure por job | A spec pede `clientId`/`clientSecret` por job; `GraphClient` usa variável de ambiente global `GRAPH_TOKEN` — não suporta multi-tenant |
| Sem truncate antes do insert | A spec diz "full replace (trunca e reimporta)"; o writer atual só faz INSERT, sem DELETE/TRUNCATE antes |
| Sem inferência de schema | A spec menciona "schema inferido dos campos da lista"; atualmente o schema deve existir previamente na tabela destino |
| Testes | Apenas `SharepointApplicationTests` com context load — sem unit/integration tests |
| Frontend | Ainda não construído (Vite + React + shadcn/ui) |
| Docker | Dockerfile não existe |
| Makefile | Não existe (build pipeline frontend → static) |

---

### ⚠️ Pontos para revisar

- **Autenticação Graph**: `GRAPH_TOKEN` é bearer token estático. A spec pede client credentials flow (clientId + clientSecret por job). Precisa trocar para `ClientSecretCredential` do Azure SDK ou MSAL.
- **`fieldMappings` vazio**: `JobRequest` valida `@NotEmpty`, mas se vier null o Jackson pode não disparar a validação — testar edge case.
- **`ScheduleType.CONTINUOUS`**: self-triggering via `scheduler.scheduleJob()` dentro do próprio job pode causar drift de tempo; revisar se é o comportamento desejado.
- **Logging MDC**: campos `requestId` e `userEmail` são setados, mas não há filtro que popule o MDC — checar se algum filtro faz isso ou se está em aberto.

---

## Variáveis de Ambiente

| Var | Default | Descrição |
|-----|---------|-----------|
| `APP_PORT` | `8080` | Porta HTTP |
| `GRAPH_TOKEN` | — | Bearer token global (provisório) |
| `GRAPH_CLIENT_ID` | — | App Registration client ID |
| `GRAPH_CLIENT_SECRET` | — | App Registration secret |
| `GRAPH_PAGE_SIZE` | `999` | Itens por página Graph |
| `DB_PATH` | `./data/sharepoint.db` | Path do SQLite |
| `WRITER_BATCH_SIZE` | `500` | Tamanho do batch JDBC |
| `RATE_LIMIT_ENABLED` | `true` | Liga/desliga rate limiting |
| `JPA_SHOW_SQL` | `false` | Log de SQL do Hibernate |

---

## Estrutura de Pacotes

```
org.migration.sharepoint
├── SharepointApplication.java
├── controller/
│   ├── MigrationJobController.java
│   └── dto/  (JobRequest, JobResponse, LogResponse)
├── core/
│   ├── job/   (SharePointMigrationJob)
│   ├── runner/ (JobReloadStartupRunner)
│   └── service/ (MigrationJobService, QuartzSchedulerService)
├── data/
│   ├── enums/ (JobStatus, ScheduleType, TargetDb, IntervalUnit)
│   ├── model/ (MigrationJob, MigrationLog)
│   └── repository/
└── infra/
    ├── config/ (ApplicationConfig, OpenApiConfig, ServerSecurityConfig, TomcatConfig)
    ├── controller/ (SpaForwardController)
    ├── converter/ (MapToJsonConverter)
    ├── exception/ (base/, custom/, handler/, DataObjectError, ErrorCode)
    ├── filter/ (CsrfCookieFilter, RateLimitingFilter)
    ├── graph/ (GraphClient)
    ├── util/ (RequestUtil)
    └── writer/ (MySqlMigrationWriter)
```
