# Decide sync-server TLS trust policy

Type: grilling
Status: resolved
Blocked by:

## Question

Will Foreground support only sync servers with publicly trusted HTTPS certificates, allowing the official TaskChampion `tls-webpki-roots` client unchanged, or must it support private/self-signed CAs? Private CA support requires a custom TaskChampion `Server`, an upstream API change, or a maintained fork because the current remote client cannot accept Android/private trust configuration.

## Answer

Foreground must support self-hosted sync servers whose HTTPS certificate chains to a user-supplied private or self-signed CA. Setup will establish an explicit trust anchor for that server; disabling certificate or hostname verification is forbidden.

Publicly trusted HTTPS remains compatible but is not sufficient as the only trust mode. Research must choose the smallest maintainable integration because TaskChampion 3.1.0's built-in remote client cannot accept custom trust roots.
