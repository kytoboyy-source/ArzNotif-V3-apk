# ArzNotif

Android live market dashboard focused on TGJU data.

## What changed in this build
- TGJU live feed (`call5.tgju.org/ajax.json`) for live prices.
- 10-second polling in the app UI (the market source controls the real data update cadence).
- TGJU history API for chart history.
- More currencies, gold, melted gold, coins, metals and crypto instruments.
- Search by Persian name, English code or TGJU profile key.
- Modern animated dashboard, bottom navigation, detail charts and animated cards.
- Advanced price/percentage alerts plus background foreground-service monitoring while alerts are active.
- Launcher/app label is `ArzNotif`.
- GitHub Actions produces an installable `ArzNotif.apk`.

## Important
The app displays TGJU as its data source and does not fabricate a price when TGJU is unavailable. A second-by-second UI clock is not the same as second-by-second market ticks; the source publishes updates on its own schedule.
