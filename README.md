# eyec

Censera's Eye is a Paper plugin for offline-mode servers that separates player authentication from player trust.

## Build

```bash
# Build the plugin without running the test suite
mvn package -DskipTests
```

Requires Java 25 and Maven 3.8 or newer.

The build produces `eyec-*.jar`. Copy it to the server's `plugins/` directory.

## Authentication

Offline-mode players authenticate with:

```text
/register <password>
/login <password> [2fa-code]
/2fa enable
/2fa confirm <code>
/2fa disable <code>
```

Censera's Eye stores accounts in `plugins/eyec/accounts.yml`.

Passwords use salted PBKDF2-HMAC-SHA256. 2FA uses TOTP.

FastLogin provides optional premium authentication. Floodgate provides Bedrock authentication through Geyser.

Authentication establishes identity. It does not establish trust.

## Visitor mode

Players remain in Visitor Mode until they are both authenticated and trusted through the server whitelist.

Visitors cannot:

- use normal commands before authentication
- damage or be damaged by entities
- interact with the world normally
- move more than 200 blocks horizontally from the configured visitor spawn
- enter the Nether or End
- use cross-world teleports

A player who authenticates without being whitelisted remains a visitor.

A whitelisted offline-mode player still has to authenticate.

## Commands

| Command | Purpose |
|---|---|
| `/eyec reload` | Reload Censera's Eye configuration |
| `/eyec list` | List relevant player state |
| `/eyec kick-visitors` | Kick players currently in Visitor Mode |
| `/guest unstuck` | Move a visitor out of an invalid position |
| `/guest nudge` | Move a visitor back toward the visitor area |
| `/register <password>` | Create an offline-mode account |
| `/login <password> [2fa-code]` | Authenticate an account |
| `/2fa <enable\|confirm\|disable> [code]` | Manage TOTP authentication |

The `eyec.admin` permission grants administrative commands.

The `eyec.bypass` permission bypasses authentication and Visitor Mode restrictions.

## Integrations

Censera's Eye supports the following optional integrations:

- `FastLogin`
- `Floodgate`
- `Geyser-Spigot`
- `ViaVersion`
- `ViaBackwards`

Censera's Eye does not hook the packet internals of Geyser, Floodgate, ViaVersion, or ViaBackwards.

## Requirements

- Paper `26.2` or newer
- Java `25`
- Maven `3.8` or newer for building

## License

Apache-2.0
