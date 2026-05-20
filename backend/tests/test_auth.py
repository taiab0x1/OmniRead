import pytest


def test_register_and_login(client, fresh_email, fresh_username):
    r = client.post("/v1/auth/register", json={
        "email": fresh_email,
        "username": fresh_username,
        "password": "Test12345",
    })
    assert r.status_code == 200, r.text
    data = r.json()
    assert data["success"] is True
    assert data["data"]["username"] == fresh_username
    assert data["data"]["tokens"]["access_token"]

    r2 = client.post("/v1/auth/login", json={"email": fresh_email, "password": "Test12345"})
    assert r2.status_code == 200, r2.text
    assert r2.json()["data"]["user_id"] == data["data"]["user_id"]


def test_register_duplicate_email(client, fresh_email, fresh_username):
    body = {"email": fresh_email, "username": fresh_username, "password": "Test12345"}
    r = client.post("/v1/auth/register", json=body)
    assert r.status_code == 200
    body2 = {**body, "username": fresh_username + "x"}
    r = client.post("/v1/auth/register", json=body2)
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "email_taken"


def test_refresh_rotates_and_detects_reuse(client, fresh_email, fresh_username):
    r = client.post("/v1/auth/register", json={
        "email": fresh_email, "username": fresh_username, "password": "Test12345",
    })
    refresh = r.json()["data"]["tokens"]["refresh_token"]
    r2 = client.post("/v1/auth/refresh", json={"refresh_token": refresh})
    assert r2.status_code == 200
    new_refresh = r2.json()["data"]["tokens"]["refresh_token"]
    assert new_refresh != refresh

    reuse = client.post("/v1/auth/refresh", json={"refresh_token": refresh})
    assert reuse.status_code == 401
    assert reuse.json()["error"]["code"] == "reuse_detected"


def test_guest_login(client):
    r = client.post("/v1/auth/guest", json={"device_fingerprint": "device-abc-12345"})
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["is_guest"] is True
    assert data["username"].startswith("guest_")


def test_invalid_password_format_rejected(client, fresh_email, fresh_username):
    r = client.post("/v1/auth/register", json={
        "email": fresh_email, "username": fresh_username, "password": "noNumbersHere",
    })
    assert r.status_code == 422
