# rbyrbt Hubitat Package Manager index

Author repository for [Hubitat Package Manager](https://hubitatpackagemanager.hubitatcommunity.com/). This repo contains the package index only; each package's code and manifest live in their own repositories.

## Packages

| Package | Repo | Description |
|---------|------|-------------|
| Lennox iComfort | [hubitat-lennox-icomfort](https://github.com/rbyrbt/hubitat-lennox-icomfort) | Manage Lennox iComfort S30/E30/S40/M30 thermostats |
| PetSafe Smart Feeder | [hubitat-petsafe-feeder](https://github.com/rbyrbt/hubitat-petsafe-feeder) | Manage PetSafe Smart Feed devices |
| Winix Air Purifiers | [hubitat-winix-purifiers](https://github.com/rbyrbt/hubitat-winix-purifiers) | Connect and control Winix air purifiers |

## Adding this repository in HPM

In Hubitat Package Manager → Settings → Add a Custom Repository, use:

```
https://raw.githubusercontent.com/rbyrbt/hubitat-rbyrbt/main/repository.json
```

To have these packages listed in the community repository for all HPM users, open a PR to [HubitatCommunity/hubitat-packagerepositories](https://github.com/HubitatCommunity/hubitat-packagerepositories) adding this repository to `repositories.json`.
