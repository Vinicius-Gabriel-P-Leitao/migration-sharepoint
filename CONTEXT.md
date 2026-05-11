Aqui o prompt de spec:

---

**Projeto: SP Migrator**

Aplicação web para migração de dados do SharePoint (via Microsoft Graph API) para bancos de dados relacionais e NoSQL de forma dinâmica, com agendamento de jobs via Quartz e interface web para gerenciamento.

**Stack**
- Java 25, Spring Boot, Gradle Kotlin DSL
- Quartz Scheduler (RAMJobStore, recarga do SQLite no boot)
- Microsoft Graph SDK + Bucket4J (rate limit nas chamadas Graph)
- SQLite + Spring Data JPA + Hibernate
- Lombok, Validation, SpringDoc/Swagger, Actuator
- Drivers: PostgreSQL, MySQL, MongoDB
- Frontend: Vite + React + shadcn/ui, servido como estático pelo Spring
- Build pipeline via Makefile (frontend build → `src/main/resources/static`)
- Deploy: Docker único container

**O que o sistema faz**
- Conecta na Graph API via Azure App Registration (client credentials)
- Extrai dados de listas do SharePoint com paginação automática
- Migra para PostgreSQL, MySQL ou MongoDB de forma dinâmica (schema inferido dos campos da lista)
- Migração é full replace (trunca e reimporta)

**Jobs**
- Criados, editados e deletados em runtime via UI
- Cada job tem: nome, credenciais Azure, siteId, listId, banco destino, connection string, tabela/collection, cron expression (opcional)
- Cron nulo = execução apenas manual
- Na inicialização do app, jobs persistidos no SQLite são recarregados no Quartz

**Logs**
- Cada execução de job gera um log salvo no SQLite
- UI exibe histórico de execuções por job: status, horário, erro se houver

**Persistência (SQLite)**
- Tabela `migration_jobs`: configuração dos jobs
- Tabela `migration_logs`: histórico de execuções

**API REST**
- CRUD de jobs
- Endpoint para disparo manual
- Endpoint para histórico de logs por job
- Swagger disponível em `/swagger-ui.html`

---

Gera o projeto com isso e me manda a estrutura que vier.