# Publishing to Google Play

Everything needed for the Play Store listing and policy forms lives in this folder.

| File | What it is |
|---|---|
| `listing.md` | App name, short and full description, category, contact details |
| `data-safety.md` | Data safety form, App content declarations, foreground service and Android Auto |
| `site/` | Public website with the privacy policy (GitHub Pages) |
| `graphics/` | Store icon, feature graphic and 8 phone screenshots |
| `make_graphics.py` | Rebuilds `graphics/` from raw emulator screenshots |

## 1. Publish the privacy policy site

Play needs a public privacy policy URL. The app's source repo stays private; the site goes in its own small
public repo on github.com (not the KP enterprise GitHub).

1. On github.com, create a **public** repo, for example `better-audiobook-player`.
   A free organization such as `aikipunk-creations` gives a neater URL
   (`https://aikipunk-creations.github.io/better-audiobook-player/`).
2. Push the site files:

   ```bash
   cp -R docs/store/site /tmp/bap-site && cd /tmp/bap-site
   git init -b main && git add . && git commit -m "Privacy policy"
   git remote add origin https://github.com/<owner>/better-audiobook-player.git
   git push -u origin main
   ```

3. In the repo, Settings → Pages → Deploy from a branch → `main` / root. After a minute the policy is at
   `https://<owner>.github.io/better-audiobook-player/privacy`.

Update `site/privacy.md` here first whenever the policy changes, then copy it over.

## 2. Build the app bundle

```bash
./gradlew :app:bundleRelease
# app/build/outputs/bundle/release/app-release.aab
```

Bump `versionCode` in `app/build.gradle.kts` before every upload after the first. Play rejects a reused code.

## 3. Play Console

1. Create the app: name "Better Audiobook Player", app, free.
2. **App signing:** keep Play App Signing on. To stay compatible with APKs you've already sideloaded, choose
   to use your existing key (upload `~/keystores/betteraudiobookplayer-upload.jks` with the PEPK tool when
   asked). Otherwise testers must uninstall the sideloaded app first.
3. **Main store listing:** paste from `listing.md`; upload `graphics/icon-512.png`,
   `graphics/feature-graphic.png` and the eight `graphics/phone-*.png`.
4. **App content:** answer from `data-safety.md`.
5. **Testing → Closed testing:** upload the AAB. New personal developer accounts need at least 12 testers
   opted in for 14 days in a row before production access.
6. **Production:** apply for production access, then roll out.

## Regenerating screenshots

Raw 1344 × 2992 emulator captures are kept outside the repo (`~/Desktop/BetterAudiobookPlayer-store/raw`).

```bash
python3 -m venv /tmp/pyv && /tmp/pyv/bin/pip install pillow
/tmp/pyv/bin/python docs/store/make_graphics.py <raw dir> docs/store/graphics
```

Use only public-domain audiobooks (for example LibriVox recordings) in screenshots.
