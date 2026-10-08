# DeviceGPT (com.teamz.lab.debugger)

## 🔴 PENDING OWNER DECISION — tell the owner at the start of every session in this repo

> **2026-10-09 (owner's yes): full-screen ads from session 4.** RC v20: `ads_grace_sessions` = 3 (skips sessions 1–3), `app_open_ad_min_session` = 4. With both at 10 (v17), full-screen ads, about 70% of ad revenue, were off for almost every user while the app kept requesting them: 263 requests and 0 impressions on 2026-10-08. Day-0 users (sessions 1–3) stay protected. Do not put these back to 10 without asking the owner.
>
> **2026-10-07 update (superseded above):** the ad stop-gap is DONE with no release — Remote Config `ads_grace_sessions` and `app_open_ad_min_session` set to 10 (v17). Both fullscreen ad paths in the LIVE build (vc50) honour it (3632d72). A 72 h install-age gate is optional polish, not required.

Churn diagnosis, 2026-10-07: `docs/CHURN-DIAGNOSIS-2026-10-07.md`. The 28-day fleet verdict shows
51 installs and 52 uninstalls.

About 60% of uninstalls happen on install day, at a **median of 4 minutes after install**.
Session 1 looks the same for people who uninstall and people who keep the app, and the keepers do
not come back either. The cause is a one-time tool sold as "camera test / mic test" with no reason
to keep it, made worse by two defects in the live build. Three fixes wait for the owner's yes:

1. **No full-screen ads until the install is 72 h old.** Today the "grace" counts activity
   creations at least 60 s apart, not days, so ads fire within the first hour. 3/3 humans who saw
   a day-0 ad uninstalled that day. Ads earn about £0.45 a month. Stop-gap with no code: set RC
   `ads_grace_sessions` and `app_open_ad_min_session` to 10.
2. **Release vc51, then stop the real-time monitor starting itself on first launch.** It starts for
   48% of new users. On vc50 it downloads 10 MB and uploads 2 MB every 30 s, on mobile data too.
3. **First screen matches the store promise, then a reason to keep the app.** After the first scan,
   land on a Camera / Mic / Screen test chooser, then offer the widget habit loop. Run it as an
   RC A/B.

Re-check: the day-0 removal share for humans (today 24/43 = 56%), about 3 weeks after the fix
ships. Remove this section once the owner has decided.
