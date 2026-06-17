#!/usr/bin/env python3
"""
Seed dummy users and devices via the running services, and dump everything
created to a JSON file so you can inspect what's in the DB without querying it.

Usage (from repo root):
    uv run scripts/seed.py
    uv run scripts/seed.py --users 5 --devices-per-user 3
    uv run scripts/seed.py --out scripts/seed-output.json
"""

import argparse
import json
import random
import string
import sys
from datetime import datetime, timezone
from pathlib import Path

import requests

DEVICE_TYPES = ["METER", "SOLAR_PANEL", "BATTERY", "APPLIANCE", "THERMOSTAT", "LIGHT", "CCTV"]

FIRST_NAMES = ["Ava", "Liam", "Noah", "Mia", "Zoe", "Kai", "Riya", "Ezra", "Luna", "Omar"]
LAST_NAMES = ["Patel", "Khan", "Singh", "Garcia", "Nguyen", "Chen", "Silva", "Muller", "Okafor", "Rossi"]
DEVICE_NAMES = {
    "METER": "Main Meter",
    "SOLAR_PANEL": "Rooftop Solar",
    "BATTERY": "Home Battery",
    "APPLIANCE": "Smart Fridge",
    "THERMOSTAT": "Living Room Thermostat",
    "LIGHT": "Kitchen Light",
    "CCTV": "Front Door Camera",
}


def unwrap(resp: requests.Response, what: str) -> dict:
    if resp.status_code >= 400:
        raise RuntimeError(f"{what} failed ({resp.status_code}): {resp.text}")
    body = resp.json()
    if not body.get("success"):
        raise RuntimeError(f"{what} returned success=false: {body.get('error')}")
    return body.get("data")


def rand_suffix(n: int = 6) -> str:
    return "".join(random.choices(string.ascii_lowercase + string.digits, k=n))


def make_user_payload() -> dict:
    first = random.choice(FIRST_NAMES)
    last = random.choice(LAST_NAMES)
    return {
        "email": f"{first.lower()}.{last.lower()}.{rand_suffix()}@example.com",
        "firstName": first,
        "lastName": last,
        "phone": f"+1-555-{random.randint(100, 999)}-{random.randint(1000, 9999)}",
        "alerting": random.choice([True, False]),
        "energyAlertThreshold": round(random.uniform(50.0, 500.0), 2),
    }


def make_device_payload(user_id: str) -> dict:
    dtype = random.choice(DEVICE_TYPES)
    return {
        "name": DEVICE_NAMES[dtype],
        "type": dtype,
        "serialNumber": f"SN-{dtype[:3]}-{rand_suffix(8).upper()}",
        "userId": user_id,
    }


def main() -> int:
    p = argparse.ArgumentParser(description="Seed dummy users and devices.")
    p.add_argument("--users", type=int, default=5, help="number of users to create (default: 5)")
    p.add_argument("--devices-per-user", type=int, default=2, help="devices per user (default: 2)")
    p.add_argument("--user-url", default="http://localhost:8081", help="user-service base URL")
    p.add_argument("--device-url", default="http://localhost:8082", help="device-service base URL")
    p.add_argument("--out", default="scripts/seed-output.json", help="where to write the JSON dump")
    p.add_argument("--seed", type=int, help="random seed for reproducible data")
    args = p.parse_args()

    if args.seed is not None:
        random.seed(args.seed)

    users_endpoint = f"{args.user_url}/api/v1/users"
    devices_endpoint = f"{args.device_url}/api/v1/devices"

    session = requests.Session()
    session.headers.update({"Content-Type": "application/json", "Accept": "application/json"})

    created_users: list[dict] = []
    created_devices: list[dict] = []
    failures: list[dict] = []

    print(f"Creating {args.users} users at {users_endpoint}")
    for _ in range(args.users):
        payload = make_user_payload()
        try:
            user = unwrap(session.post(users_endpoint, json=payload, timeout=10), "create user")
        except (RuntimeError, requests.RequestException) as e:
            print(f"  [skip] {payload['email']}: {e}", file=sys.stderr)
            failures.append({"kind": "user", "payload": payload, "error": str(e)})
            continue
        created_users.append(user)
        print(f"  + user {user['id']}  {user['email']}")

    print(f"\nCreating {args.devices_per_user} devices per user at {devices_endpoint}")
    for user in created_users:
        for _ in range(args.devices_per_user):
            payload = make_device_payload(user["id"])
            try:
                device = unwrap(session.post(devices_endpoint, json=payload, timeout=10), "create device")
            except (RuntimeError, requests.RequestException) as e:
                print(f"  [skip] {payload['serialNumber']}: {e}", file=sys.stderr)
                failures.append({"kind": "device", "payload": payload, "error": str(e)})
                continue
            created_devices.append(device)
            print(
                f"  + device {device['id']}  {device['type']:<11} "
                f"{device['serialNumber']}  -> user {user['id']}"
            )

    devices_by_user: dict[str, list[dict]] = {u["id"]: [] for u in created_users}
    for d in created_devices:
        devices_by_user.setdefault(d["userId"], []).append(d)

    dump = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "endpoints": {"users": users_endpoint, "devices": devices_endpoint},
        "summary": {
            "users": len(created_users),
            "devices": len(created_devices),
            "failures": len(failures),
        },
        "users": created_users,
        "devices": created_devices,
        "devicesByUser": devices_by_user,
        "failures": failures,
    }

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(dump, indent=2, default=str))

    print(f"\nDone. Created {len(created_users)} users and {len(created_devices)} devices.")
    print(f"Dump written to {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
