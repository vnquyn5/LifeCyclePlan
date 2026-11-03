# AUTH-02 local JWT signing keys

## Direct Spring Boot run on macOS

The default Spring profile is `local` (`spring.profiles.default: local`). When
that profile is active, `application-local.yml` imports
`./application-local-secrets.properties`. Run Maven from the `backend`
directory so the relative config import and key paths resolve from that
directory.

The JWT configuration uses Spring's `Environment` through `JwtProperties`
(`@ConfigurationProperties`), not a direct `System.getenv()` lookup. The
tracked `application.yml` reads `JWT_PRIVATE_KEY_PATH` and
`JWT_PUBLIC_KEY_PATH` as Spring properties and defaults to:

```text
./.local-secrets/jwt-private.pem
./.local-secrets/jwt-public.pem
```

The existing ignored `backend/application-local-secrets.properties` supplies
local database and other development settings. It does not need duplicate JWT
path entries while those keys remain at the default paths. Root `.env` is read
by Docker Compose; Spring Boot's Maven command does not source that file as an
OS environment file. If overriding the key paths for a direct run, add the
properties to the already imported local secrets file or export the two
environment variables in that terminal.

### Start the backend

After confirming the local database is backed up and its Flyway V3/V4 migration
state is ready, open a new Terminal and run:

```sh
cd /path/to/LifeCyclePlan/backend
./mvnw spring-boot:run
```

The process reads the ignored local secrets file and the default key paths.
Spring Boot has Flyway enabled, so startup can apply pending migrations. Do not
run this against the project database until its migration state and backup have
been reviewed.

## Existing key files

The current local pair is expected at `backend/.local-secrets/`. Do not
regenerate or overwrite an existing pair during normal setup. For a new local
environment only, create a persistent 3072-bit PKCS#8 RSA pair:

```sh
mkdir -p backend/.local-secrets
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 \
  -out backend/.local-secrets/jwt-private.pem
openssl pkey -in backend/.local-secrets/jwt-private.pem -pubout \
  -out backend/.local-secrets/jwt-public.pem
chmod 600 backend/.local-secrets/jwt-private.pem
```

The private key must remain owner-readable only. The current file mode is
`600`; the public key is `644`. Both key files and the local secrets properties
file are ignored by Git.

Verify the pair without printing key contents:

```sh
openssl pkey -in backend/.local-secrets/jwt-private.pem -check -noout
cmp <(openssl pkey -in backend/.local-secrets/jwt-private.pem -pubout -outform DER) \
    <(openssl pkey -pubin -in backend/.local-secrets/jwt-public.pem -outform DER)
```

The Backend key loader also checks that both files are valid RSA keys of at
least 2048 bits and that the public/private pair matches. Never paste key
contents into logs, reports, chat, or source control.

## Docker Compose

Compose uses a separate container path mapping. It sets
`JWT_PRIVATE_KEY_PATH=/run/secrets/jwt-private.pem` and
`JWT_PUBLIC_KEY_PATH=/run/secrets/jwt-public.pem`, then mounts the local
`backend/.local-secrets` directory read-only at `/run/secrets`. Compose reads
the root `.env` for its database and other container variables. These paths
are independent of the relative paths used by a direct Maven run.

## AUTH-02 policy and prerequisites

- Signing algorithm: RS256.
- Access JWT lifetime: 15 minutes (900 seconds).
- Refresh token lifetime: 7 days, with rotation.
- The Backend does not create ephemeral keys at startup; it fails startup if
  configured key files are missing, malformed, too small, or mismatched.
- `JWT_ISSUER` can override the development issuer.
- V3/V4 Flyway migration status for the target database must be verified before
  starting the full application. Startup runs Flyway and Hibernate schema
  validation; do not use the project's local database for a configuration-only
  check.
- Production key management and policy require a separate security review.
