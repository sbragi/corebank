# CoreBank

Projeto de referência de uma plataforma de conta digital em Java 21 / Spring Boot para demonstrar:

- Redis como cache de saldo
- PostgreSQL como fonte de verdade
- CDC com Debezium Server + PostgreSQL WAL + Redis Streams
- Idempotência para PIX e autorização de cartão
- Pessimistic locking para autorização financeira
- Flyway
- Docker Compose
- Testes unitários com Mockito
- Testes de integração com Testcontainers
- Teste de performance em Java 21

## Arquitetura

Com esta arquitetura conseguiremos manter a resiliencia busca do saldo sem sobrecarregar o banco principal.
A leitura do saldo será feita somente no Redis com execeção do endPoint de autorização do cartão

O Trade off seria a carga incial do Redis, que incialmente estará vazio e so será atualizado pelo CDC quando alguma alteração ocorrer no Postgrees
Será necessário criar um script para fazer a carga inicial, após isso o sistema funcionará conforme planejado

```text
                     +----------------+
                     |    Client      |
                     +-------+--------+
                             |
                             v
                     +---------------+
                     | Spring Boot   |
                     +---+-------+---+
                         |       |
                    read |       | write
                         v       v
                     +------+ +----------+
                     |Redis | |PostgreSQL|
                     +------+ +-----+----+
                         ^          |
                         |          | WAL / logical decoding
                         |          v
                         |    +-------------+
                         +----|  Debezium   |
                              |    Server   |
                              +------+------+
                                     |
                                     v
                               Redis Stream
                                     |
                                     v
                              CDC Processor
                                     |
                                     v
                                Redis cache
```

## Requisitos

- Java 21
- Maven 3.9+
- Docker Desktop / Docker Engine

## Executar localmente

```bash
mvn clean package

docker compose up --build
```

A API estará em `http://localhost:8080`.

Health:

```bash
curl http://localhost:8080/actuator/health
```

## Conta inicial

```text
00000000-0000-0000-0000-000000000001
```

Saldo inicial: `1000.00`.

## Consultar saldo

```bash
curl http://localhost:8080/api/v1/accounts/00000000-0000-0000-0000-000000000001/balance
```

## PIX

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: pix-001' \
  -d '{"accountId":"00000000-0000-0000-0000-000000000001","amount":100.00}'
```

Repetir a mesma requisição com `pix-001` retorna o mesmo resultado sem debitar duas vezes.

## Cartão

```bash
curl -X POST http://localhost:8080/api/v1/cards/authorizations \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: card-001' \
  -d '{"accountId":"00000000-0000-0000-0000-000000000001","amount":50.00}'
```

A autorização usa `SELECT ... FOR UPDATE` para garantir que a decisão financeira seja tomada no PostgreSQL atual, e não no cache.

## Testes

Unitários:

```bash
mvn test
```

Os testes unitários usam mocks e não precisam de Docker.

Integração:

`AccountRepositoryIntegrationTest` usa Testcontainers para executar PostgreSQL real.

JaCoCo:

```text
target/site/jacoco/index.html
```

Thresholds configurados:

- Line coverage >= 80%
- Branch coverage >= 70%

## Performance

O teste de performance é excluído do `mvn test` normal.

Subir a aplicação primeiro:

```bash
docker compose up --build
```

Depois executar:

```bash
mvn -Dtest=BalancePerformanceTest -Dperformance.requests=5000 -Dperformance.concurrency=100 test
```

O teste usa `java.net.http.HttpClient` e virtual threads do Java 21.

## Decisões arquiteturais

### PostgreSQL

É a fonte de verdade do saldo. O saldo financeiro nunca é autorizado a partir do Redis.

### Redis

É usado para leituras de saldo de alta frequência. O TTL padrão é 5 segundos.

### CDC

O PostgreSQL usa `wal_level=logical`. Debezium captura as mudanças através de `pgoutput` e publica os eventos em Redis Streams. O Spring Boot consome o stream e atualiza o cache.


### Idempotência

PIX e cartão exigem `Idempotency-Key`. A chave é persistida com o resultado da transação para permitir replay seguro.

### Concorrência

A autorização financeira utiliza pessimistic locking no registro da conta. Isso evita que duas operações concorrentes leiam o mesmo saldo e autorizem gastos incompatíveis.

### Replica PostgreSQL

O ambiente local utiliza uma única instância PostgreSQL para simplificar o desenvolvimento. A aplicação pode posteriormente ser adaptada para separar leitura e escrita com uma réplica gerenciada em produção.

## Testing strategy

The default test suite does **not require Docker**. Unit tests mock external infrastructure such as PostgreSQL repositories and Redis using Mockito.

```bash
mvn clean test
```

This is the recommended command during development and CI for fast unit tests.

Integration tests are optional and use Testcontainers to start a real PostgreSQL container:

```bash
mvn -Pintegration test
```



