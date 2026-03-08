# Hubitat Packages

Single repository for [Hubitat Package Manager](https://hubitatpackagemanager.hubitatcommunity.com/) (HPM). All package code and manifests live in this repo.

## Packages

| Package | Info | Description |
|---------|--------|-------------|
| Lennox iComfort | [README.md](LennoxiComfort/README.md) | Manage Lennox iComfort S30/E30/S40/M30 thermostats |
| PetSafe Smart Feeder | [README.md](PetSafeSmartFeeder/README.md) | Manage PetSafe Smart Feed devices |
| Winix Air Purifiers | [README.md](WinixAirPurifiers/README.md) | Connect and control Winix air purifiers |

## Adding this repository in HPM

In Hubitat Package Manager → Settings → Add a Custom Repository, use:

```
https://raw.githubusercontent.com/rbyrbt/Hubitat/main/repository.json
```

This single URL serves the intermediate manifest; HPM then loads each package from the paths defined there (e.g. `LennoxiComfort/packageManifest.json`).

To have these packages listed in the community repository for all HPM users, open a PR to [HubitatCommunity/hubitat-packagerepositories](https://github.com/HubitatCommunity/hubitat-packagerepositories) adding this repository to `repositories.json`.
