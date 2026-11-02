# Built-in VPN (WireGuard)

## What it is

An optional, full-device WireGuard tunnel built on Android's `VpnService`. It uses the official
`com.wireguard.android:tunnel` library (Apache-2.0, `GoBackend`). The user imports a configuration
from a VPN provider or a server they run themselves. **No servers, accounts or keys are bundled**,
and the app never contacts a provider API to obtain them.

It exists for privacy on untrusted networks. It is not a way around streaming licences: region
errors from a provider are shown as-is, and nothing in the app selects an exit country to unlock
content.

## Architecture

| Class | Responsibility |
| --- | --- |
| `VpnConfigAnalysis` | Parses and validates the config text (16 KB cap). Requires `[Interface] PrivateKey, Address` and, per peer, `PublicKey, AllowedIPs, Endpoint`. Refuses `PreUp/PostUp/PreDown/PostDown` shell hooks. Reports full or split tunnel per IP family, DNS, endpoint, and warnings |
| `VpnConfigStore` | Seals the config with an Android Keystore AES-GCM key (`astra-vpn-config-v1`) in `astra_ui_v1`. Removing the config deletes the key. Also stores leak protection and auto-reconnect (both on by default) |
| `VpnController` | Singleton around `GoBackend`: consent, connect, disconnect, statistics poll every 3 s, reconnect with backoff, network-change callbacks, app-level playback gate |
| `VpnHealth` | Pure state machine: `NOT_CONFIGURED, OFF, CONSENT_REQUIRED, CONNECTING, AWAITING_HANDSHAKE, PROTECTED, PARTIAL, STALE, RECONNECTING, FAILED` |
| `NetworkStatus` | Reads OS VPN networks: presence, DNS, and the app's own VPN `Network` for probes |
| `AstraApp` | Creates the controller at process start so always-on callbacks are registered |

## When the app says "Protected"

All of the following must be true. Otherwise the label says exactly which part is missing.

1. Android reports a network with `TRANSPORT_VPN`.
2. The WireGuard tunnel is `UP`.
3. A peer handshake happened within the last 180 s.
4. The tunnel is full: IPv4 `0.0.0.0/0`, plus IPv6 `::/0` or IPv6 leak protection.

If the tunnel is up but no handshake has arrived, the state is "Tunnel created · waiting for the
server handshake · not protected yet". A handshake older than 180 s is reported as stale, and a
split tunnel as partial.

## Leak protection

- **IPv6:** with leak protection on, `::/0` is added to every peer before the tunnel starts, so IPv6
  can only enter the tunnel. If the server has no IPv6, IPv6 traffic fails instead of leaking.
- **DNS:** the config's `DNS` servers are applied to the tunnel. Configs without DNS get a warning.
  While protected, the Security Dashboard retest (AST-13) fails if the VPN network reports any
  DNS server that is not in the config.
- **Latency probe:** a TCP connection to the tunnel's DNS server, bound to the VPN `Network`. It is
  never measured on the normal connection and labelled as tunnel latency.
- **Exit check:** asks Cloudflare's trace endpoint for the exit address. When the tunnel is not
  protected, the result is explicitly labelled as the normal connection.

## Kill switch

- **OS-level (real):** Android Settings → VPN → Saandio TV → Always-on VPN + Block connections
  without VPN. The app shows whether both are enabled (`isAlwaysOn`, `isLockdownEnabled`) and links
  to that screen.
- **App-level:** with "Require VPN before playback" on, playback refuses to start unless the
  tunnel is Protected (or another VPN app is active while Saandio's tunnel is off). Playback is
  paused if protection drops. This covers playback only, not other apps.

## Reconnect and network changes

When the backend reports the tunnel down while the user still wants it, the app reconnects with
backoff of 2 s doubling to 30 s, at most 6 attempts, then shows a failure message. Underlying
network changes (Wi-Fi ↔ Ethernet, link loss) trigger an immediate refresh.

## Provider comparison (integration permission)

Saandio TV behaves like the official WireGuard app: the user brings a config generated for their own
device. No provider below has granted permission to embed its network in another app, and none is
assumed. Plan details change, so verify each provider's current terms before relying on them.

| Option | WireGuard config for third-party clients | Free tier | Fit |
| --- | --- | --- | --- |
| Self-hosted WireGuard (home router or VPS) | Yes, you own it | n/a | Best for testing and full control |
| Mullvad | Config generator for own devices | No | Good. Paid, no account e-mail |
| Proton VPN | Config download for own devices; free-plan limits apply | Yes, limited | Check the plan's terms for manual configs |
| IVPN | Config generator | No | Good |
| Windscribe | Config generator, plan-dependent | Yes, limited | Check the plan's terms |
| Cloudflare WARP | Only through Cloudflare's own app; unofficial extraction tools are not sanctioned | Yes | Not supported |

## Performance and validation (2026-11-02, Android 16 emulator)

| Check | Result |
| --- | --- |
| Invalid config (missing `Address`) | Rejected: "[Interface] needs an Address." |
| Malformed config (setting before any section) | Rejected |
| Consent declined | "Android VPN permission was not granted, so no tunnel was created." |
| Endpoint 192.0.2.1 (documentation range, no server) | Tunnel up, OS reports VPN connected, app shows **not protected** for the whole 55 s observation |
| Tunnel setup time | 1,981 ms first start, 1,789 ms cold process, 290 ms warm (handshake not included) |
| Latency with no handshake | "not measured · needs a working tunnel" (fixed: an earlier build measured 14 ms on the normal network) |
| Require VPN + unprotected tunnel | Playback refused with the current state shown |
| Disconnect | OS VPN networks: 0 |
| Remove configuration | Encrypted config, label and Keystore key deleted |
| Config persistence across app update | Kept (Keystore key survives `install -r`) |

Not measured: handshake time, throughput and real exit location. No real WireGuard server was
available to this test environment, and no third-party VPN infrastructure was used. Unit tests:
`VpnTest` (7) covers config validation, hooks refused, IPv6 block insertion, redaction, the state
machine, and backoff.

## Cost and limitations

- The debug APK grows from 6.47 MB to 20.88 MB, mostly `libwg-go.so` for four ABIs. Ship an AAB or
  ABI splits to cut per-device size to about a quarter of that.
- `UAPIOpen: mkdir /data/data/com.wireguard.android: permission denied` appears in logcat. It is
  the library's optional control socket for the `wg` CLI, which embedded apps cannot create. The
  tunnel is unaffected.
- One tunnel and one config at a time. No per-app split tunnelling UI.
- Android allows one active VPN. Starting Saandio's tunnel replaces another VPN app's tunnel.
