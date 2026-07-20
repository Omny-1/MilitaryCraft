# Security Policy

## Supported versions

The most recent release is the only version that receives fixes.

| Version | Supported |
| --- | --- |
| 1.0.x | Yes |

## Reporting a vulnerability

Please do not open a public issue for a security problem. A public report tells every
server running the plugin how to exploit it before a fix exists.

Instead, use GitHub's private reporting: go to the **Security** tab of this repository and
choose **Report a vulnerability**. That creates a private advisory visible only to the
maintainer.

Useful things to include are the plugin and server versions, which module is affected, the
steps to reproduce, and what an attacker gains. A proof of concept helps but is not
required.

## Scope

The kinds of problem worth reporting here are ones a player can trigger on a server without
operator rights. Examples include a command or item that bypasses the permission gate,
a way to damage or remove another player's vehicle without the intended permission, an
input that crashes or hangs the server, and anything allowing a player to affect a world or
region they should not reach.

Being able to cause damage while legitimately holding operator rights, or a server owner
configuring the plugin in an unsafe way, are not security problems. Ordinary bugs and
balance complaints belong in a normal issue.
