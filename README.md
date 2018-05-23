# butterflynet

Collects raw documents (PDF, Word) and spits out a link to the web archive. Not useful for HTML.

### Configuration

Environment Variable|Default|Description
--------------------|-------|------------
BUTTERFLYNET_DB_URL |jdbc:h2:mem:butterflynet|JDBC URL of SQL database, default is in-memory
BUTTERFLYNET_DB_USER|butterflynet|Username for database
BUTTERFLYNET_DB_PASSWORD|butterflynet|Password for database
BUTTERFLYNET_WARC_DIR|./warcs/|Directory which WARCs will be written to
BUTTERFLYNET_WARC_PREFIX|WEB|Prefix used when naming WARC files
OAUTH_SERVER_URL|(none)|URL of the OpenID Connect server identity provider
OAUTH_CLIENT_ID|(none)|OAuth client id to retrieve user profile with
OAUTH_CLIENT_SECRET|(none)|OAuth client secret
