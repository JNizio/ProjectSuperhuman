# Project Superhuman PC Companion

Small Windows companion for Project Superhuman. It starts at Windows sign-in, measures PC uptime from the Windows monotonic boot clock, and exposes the current reading to your phone over your private LAN.

## Install

Requires Python 3 on Windows.

1. Open PowerShell in this folder.
2. Run: `powershell -ExecutionPolicy Bypass -File .\install_startup.ps1`
3. If Windows Firewall prompts, allow **Private networks only**.
4. For first pairing, run `python superhuman_pc.py` in a terminal once. It prints the PC's LAN endpoint and pairing token.
5. On the phone, request `/status` with header `Authorization: Bearer <token>`.

Port defaults to **8765**. The service binds to the LAN but status data is protected by a randomly generated local token stored in `%LOCALAPPDATA%\ProjectSuperhuman\pc-companion\token.txt`.

## Current data

- PC hostname
- observation timestamp (UTC)
- Windows boot timestamp (UTC)
- uptime seconds/minutes/hours
- explicit provenance: `windows.GetTickCount64`

This is deliberately v0.1. It does not inspect files, browser history, keystrokes, microphone, camera, or application contents.

## Test from another device

With the phone on the same Wi-Fi, use an HTTP client against the printed endpoint and include the bearer token. Android integration can poll this endpoint periodically while the app is active and store observations in the Data Vault.

## Note on "PC on time"

Windows uptime includes time across some sleep/hibernate scenarios depending on system behavior. The next version should record power/session transitions so Project Superhuman can distinguish **powered on**, **awake**, **unlocked**, and **actively used** time.
