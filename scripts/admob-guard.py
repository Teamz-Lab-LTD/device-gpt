#!/usr/bin/env python3
"""DeviceGPT AdMob guard: one line if ads are healthy, one line per problem if not.

    python3 scripts/admob-guard.py            # last 7 days vs the 7 before
    python3 scripts/admob-guard.py --days 7

Built 2026-10-08 after a month in which three ad faults hid inside one blended "show rate":
a single phone looping native requests (1,279 in two days), native loading starved by a UMP
consent race (30-60 requests/day fell to 1-5/day from 3.1.27), and a placeholder rewarded unit.
Each check below is one of those failure shapes. Exit code 1 when anything is flagged.
"""
import argparse, os, subprocess, sys, statistics
from collections import defaultdict

ADMOB = os.path.expanduser("~/Projects/Teamz Lab Projects/teamz-projects/teamz-company-automation/py/admob.py")
UNITS = {  # DeviceGPT ad units (AdMob account pub-7088022825081956)
    "ca-app-pub-7088022825081956/2139601263": "native",
    "ca-app-pub-7088022825081956/5139119290": "interstitial",
    "ca-app-pub-7088022825081956/3200748520": "app_open",
}

def pull(days):
    out = subprocess.run([sys.executable, ADMOB, "report", "--days", str(days), "--dimensions", "DATE,AD_UNIT",
                          "--metrics", "AD_REQUESTS,MATCHED_REQUESTS,IMPRESSIONS,CLICKS,ESTIMATED_EARNINGS"],
                         capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit(f"ADMOB GUARD: could not read AdMob ({out.stderr.strip()[:200]})")
    rows = defaultdict(dict)
    for line in out.stdout.splitlines():
        f = line.split("\t")
        if len(f) == 7 and f[1] in UNITS and f[0].isdigit():
            rows[UNITS[f[1]]][f[0]] = dict(req=int(f[2]), matched=int(f[3]), imp=int(f[4]), clicks=int(f[5]), micros=int(f[6]))
    return rows

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--days", type=int, default=7); a = ap.parse_args()
    rows = pull(a.days * 2)
    problems, earned = [], 0
    for fmt, by_day in rows.items():
        days = sorted(by_day)
        recent, prior = days[-a.days:], days[:-a.days]
        tot = lambda ds, k: sum(by_day[d][k] for d in ds)
        earned += tot(recent, "micros")
        req_r, req_p = tot(recent, "req"), tot(prior, "req")
        # 1. loop: one day far above the usual day
        daily = [by_day[d]["req"] for d in days]
        med = statistics.median(daily) if daily else 0
        for d in recent:
            if by_day[d]["req"] >= max(100, 8 * med):
                problems.append(f"{fmt}: {by_day[d]['req']} requests on {d} (usual day ~{med:.0f}) - request loop on some device")
        # 2. starved: requests collapsed vs the week before
        if req_p >= 50 and req_r < 0.3 * req_p:
            problems.append(f"{fmt}: requests fell {req_p} -> {req_r} week on week - loading is blocked (check consent/RC/premium gates)")
        # 3. dead unit: asking but never served
        if req_r >= 20 and tot(recent, "matched") == 0:
            problems.append(f"{fmt}: {req_r} requests, 0 served - unit id wrong or disabled")
        # 4. accidental clicks
        imp, clicks = tot(recent, "imp"), tot(recent, "clicks")
        if fmt == "interstitial" and imp >= 20 and clicks / imp > 0.10:
            problems.append(f"{fmt}: {clicks} clicks on {imp} impressions ({clicks/imp:.0%}) - accidental clicks, AdMob invalid-traffic risk")
        # 5. native served but not seen
        m = tot(recent, "matched")
        if fmt == "native" and m >= 50 and tot(recent, "imp") / m < 0.05:
            problems.append(f"native: {tot(recent,'imp')} shown of {m} served - ads load where users never scroll")
    if not rows:
        problems.append("no DeviceGPT ad units in the report - every format stopped requesting")
    money = f"earned £{earned/1e6:.2f} in {a.days} days"
    if problems:
        print(f"ADS: {len(problems)} problem(s), {money}")
        for p in problems: print("  - " + p)
        sys.exit(1)
    print(f"ADS OK - {money}")

if __name__ == "__main__":
    main()
