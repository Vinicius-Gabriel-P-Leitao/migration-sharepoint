# SP Migrator - Contexto Técnico (Backend)

Este documento é a especificação técnica exaustiva da API do backend, mapeada diretamente do Swagger (`/v3/api-docs`).

## 📡 Endpoints (`/v1`)

| Método | Rota | Resumo |
| :--- | :--- | :--- |
| **GET** | `/v1/connections` | Lista todas as conexões (Key e Name). |
| **POST** | `/v1/connections` | Registra uma nova conexão (URL do banco). |
| **DELETE** | `/v1/connections/{key}` | Remove uma conexão pelo identificador único. |
| **POST** | `/v1/sharepoint/resolve` | Resolve URL de lista em SiteId, ListId e Colunas. |
| **GET** | `/v1/adapters/{targetDb}/types` | Consulta tipos suportados pelo banco destino. |
| **GET** | `/v1/jobs` | Lista todos os jobs de migração. |
| **POST** | `/v1/jobs` | Cria e agenda um novo job. |
| **GET** | `/v1/jobs/{id}` | Detalhes de um job específico. |
| **PUT** | `/v1/jobs/{id}` | Atualiza todas as configurações de um job. |
| **DELETE** | `/v1/jobs/{id}` | Remove um job e cancela agendamento. |
| **POST** | `/v1/jobs/{id}/run` | Executa um job imediatamente (async). |
| **GET** | `/v1/jobs/{id}/logs` | Histórico completo de logs de um job. |

---

## 📦 Schemas de Dados (Objetos)

### `FieldMapping`
Mapeamento individual de uma coluna.
- `column` (string): Nome da coluna no SharePoint.
- `type` (enum): `TEXT`, `NUMBER`, `DECIMAL`, `BOOLEAN`, `DATE`, `DATETIME`.
- `nativeType` (string): Tipo nativo no banco destino (ex: `VARCHAR(255)`).

### `JobRequest`
Dados para criação/atualização de job.
- `name` (string, minLength: 1): Nome do job.
- `siteId` (string, minLength: 1): ID do site SharePoint.
- `listId` (string, minLength: 1): ID da lista SharePoint.
- `pageSize` (integer, 1-5000): Quantidade de itens por página na Graph API.
- `fieldMappings` (object, minProperties: 1): Map de `targetColumn` -> `FieldMapping`.
- `targetDb` (enum): `MYSQL`, `POSTGRESQL`, `MONGODB`.
- `connectionKey` (string, minLength: 1): Chave da conexão registrada.
- `tableName` (string, minLength: 1): Nome da tabela/coleção destino.
- `scheduleType` (enum): `MANUAL`, `INTERVAL`, `CRON`, `CONTINUOUS`.
- `intervalValue` (long): Valor numérico para o intervalo.
- `intervalUnit` (enum): `MINUTES`, `HOURS`, `DAYS`.
- `cronExpression` (string): Expressão Cron válida.
- **Obrigatórios**: `connectionKey`, `fieldMappings`, `listId`, `name`, `pageSize`, `scheduleType`, `siteId`, `tableName`, `targetDb`.

### `JobResponse`
Extensão do `JobRequest` com campos de leitura.
- `id` (long): ID incremental.
- `createdAt` (date-time): Data de criação.
- `updatedAt` (date-time): Última atualização.
- *(Inclui todos os campos do JobRequest)*.

### `SharePointResolveRequest`
- `url` (string, minLength: 1): URL completa da lista SharePoint.

### `SharePointResolveResponse`
- `siteId` (string): ID resolvido do Site.
- `listId` (string): ID resolvido da Lista.
- `columns` (array<string>): Lista de nomes internos das colunas.

### `ConnectionRequest`
- `key` (string, pattern: `[A-Z0-9_]+`): Identificador único (ex: `PROD_DB`).
- `name` (string): Nome amigável.
- `url` (string): JDBC URL ou Mongo URI.

### `ConnectionSummary`
- `key` (string): Identificador.
- `name` (string): Nome amigável.

### `LogResponse`
- `id` (long): ID do log.
- `jobId` (long): ID do job relacionado.
- `status` (enum): `RUNNING`, `SUCCESS`, `FAILED`.
- `startedAt` (date-time): Início da execução.
- `finishedAt` (date-time, opcional): Fim da execução.
- `errorMessage` (string, opcional): Detalhes se falhar.

### `AdapterTypesResponse`
- `canonical` (object): Map de `CanonicalType` -> `Exemplo/Descrição`.
- `nativeTypes` (array<string>): Sugestões de tipos nativos do banco.

---

## 🛡️ Restrições e Segurança
- **Regex de Tabela**: O backend valida se `tableName` e as chaves de `fieldMappings` seguem padrões de SQL seguro.
- **In-Memory Connections**: O `ConnectionRequest.url` é sensível e **nunca** é persistido no banco SQLite; fica apenas em memória durante o runtime do backend. Se o backend reiniciar, ele busca de variáveis de ambiente `CONN_URL_*`.
