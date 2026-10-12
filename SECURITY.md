# Security Policy

## Supported versions

Thrym is maintained by a single person. Security fixes are made for the **latest release** only, and for the `main` branch. Before you report an issue, please check whether it still occurs on the latest version.

Thrym has no prebuilt downloads yet. Until there are, "latest release" means the current state of `main`.

## Reporting a vulnerability

**Please do not report security issues in public issues, pull requests or discussions.**

Report them privately through GitHub:

1. Open the repository's **Security** tab.
2. Click **Report a vulnerability**.
3. Describe the issue, the affected platform (Android or iOS) and app version, the steps to reproduce it, and the impact you expect.

Only you and the maintainer can see the report. You can expect a first reply within about a week. We'll work out the fix and the disclosure together with you, and we're happy to credit you in the advisory unless you'd rather stay anonymous.

If the **Report a vulnerability** button is missing, private reporting has been switched off by mistake. In that case, open an issue titled "Security contact request" that only asks the maintainer for a private way to reach them. Leave out every detail of the vulnerability.

Please use test data only. Never send real training data, real backup files or incident reports that contain personal information. If a crafted file is needed to reproduce the issue, attach a minimal one that you made yourself.

## Scope

Thrym is an offline app. It has no server, no account and no network code, and the Android app does not request the internet permission. The relevant attack surface is therefore on the device:

- **Backup import:** parsing and validating a backup JSON file chosen by the user (`:shared`, `com.thrym.shared.backup`). Examples are malformed or malicious files that crash the app, corrupt data or get past the fail-closed validation, or files that use excessive resources.
- **File sharing:** the Android `FileProvider` (`${applicationId}.fileprovider`) and the sharing of incident reports and backups. Examples are access to files outside the intended paths, or data in an incident report that it should not contain.
- **Local data at rest:** how the Room database (Android), the training state in Application Support (iOS), the internal safety backups and the settings are stored, and whether other apps can access them.

Out of scope:

- Attacks that need a rooted or jailbroken device, or physical access to an unlocked device
- Backup files that the user exports and then stores or shares somewhere insecure. Backups are unencrypted by design, see the [privacy section of the README](README.md#privacy).
- Vulnerabilities in the operating system or in third-party libraries, unless Thrym uses them in an exploitable way. Please report those upstream.
- Findings from automated scanners without a demonstrated impact

## Bug bounty

There is no bug bounty program. Thrym is a volunteer open-source project, and we can't offer payment for reports. We do appreciate them, and we'll credit you if you wish.
