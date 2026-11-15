# Leyden PWAs

Turn any website into its own Android app, with customization, privacy and clarity built in.

## Why

I wanted to make PWAs with customization, privacy, clarity and usefulness built in, and couldn't find any on Google Play, or ones I could trust online. So I built one.

## What it does

**Apps from any site**
- Each app gets its own icon, its own window in recents, and its own storage and logins.
- The page always stays above the keyboard.
- Before you add a site, Leyden checks it in a throwaway browser and shows you the results as they come in: whether it responded, trackers, cookies from other sites, scripts, and certificate problems.
- If a site can't be checked or won't load, you can add or open it anyway, or start with the lowest protections and turn ad blocking and fingerprinting protection back on one step at a time, undoing whatever breaks it.
- **Security checks** that refuse protected browsers, such as Cloudflare, hCaptcha, reCAPTCHA, or a site's own check page, can get through without giving up protection on the rest of the site.
  - **Known check services** are let through automatically.
  - **One-time:** you can lower protections just until you're past a check.
  - **Specific pages:** you can list pages that always get lower protections.

**Privacy you can see**
- You choose what Leyden remembers for each app. Off means it's never written.
- Ad and tracker blocking uses EasyList, EasyPrivacy and your own rules, per app or for all of them.
- You get warnings for sites that aren't encrypted, are known for tracking, or send you somewhere you didn't tap.
- Apps can be locked with a PIN or fingerprint. Nothing loads until you unlock it.
- Private mode wipes the app when it closes.
- **Peek** opens any site, or any of your apps, with no logins, scripts or saved data attached. You choose the privacy settings first, and can turn a peek into a full app from its quick settings, keeping what you signed into if you want.
- **Private** opens with strict blocking (it can step down to standard, never off), remembers nothing, and erases everything when closed. Both keep their storage separate from your apps, and locked apps still need your PIN or fingerprint first.
- Each app has a storage page that lists everything kept for it, with clear buttons.
- **Fingerprinting protection** is set per app: off, standard, strict, or your own mix.
  - **Hides:** your phone model, the app header, hardware details and your local network address.
  - **Can blur:** canvas, WebGL and audio, with a pattern that stays steady within an app.
  - **Can fake:** screen size, media devices, battery, timezone and language.
  - **Shows:** what each page tried to read, and the site check counts it before you add a site.

**Make sites work your way**
- Add your own styles and scripts, or install userscripts and userstyles from a web address or a Git repository. You can read the whole file before anything is added.
- Each insertion has its own triggers, conditions and load order, with a test panel that shows what it's doing on the page.
- Select lets you hide parts of a page, or target them for scripts and auto actions.
- Buttons, volume keys and swipes can run built-in actions or your scripts.

**Everyday extras**
- Quick settings has search, find on the page, and toggles.
- Links can open in the right app, with rewrites like reddit.com → old.reddit.com.
- You can share text, links and files into an app.
- Addresses can change by network, like your home Wi-Fi or Tailscale.
- Proxy for chosen sites only.
- Background audio, picture-in-picture, and bubbles. What's playing, on any page in the app, shows on your list and in quick settings with controls, unless you turn "what's playing" off.
- A download folder for each app, plus full-page screenshots.
- Second copies of an app, each with its own login.
- Works with your password manager through Android autofill.

**Your home screen**
- Search your apps, their recent pages, or type an address to add one.
- Running apps show what you left them on.
- Group apps however you like.
- The app's color follows your phone or one you pick.

## Build

Needs JDK 17+ and the Android SDK (platform 37, build-tools 36).

```
sh scripts/find-sdk.sh
./gradlew assembleDebug
sh scripts/run.sh
```

Run the tests with `./gradlew test`.

Separate storage per app needs Android System WebView 110 or newer. Settings › about this phone shows what works on your device.

## Limits

- Passkeys don't work inside apps like this. Android only allows them in browsers and a site's own app.
- Android allows one proxy for the whole app. Your list decides which sites use it.
- Bubbles need Android 11+. The blur reveal needs Android 12+.
- Files can only be shared into sites that accept them in their manifest.
- Blocking can't stop ads served from a site's own server, scriptlet filters, or WebSocket traffic.
- Fingerprinting protection can't change your IP address, how the connection itself looks, or what runs in background workers. A determined site can tell values were changed.

## Code

All in `app/src/main/kotlin/org/bond/pwaapp`, plain Kotlin with no AppCompat. The filter engine, rules and selector parsing are covered by unit tests.

| Area | Files |
|---|---|
| Apps and settings | `Site.kt`, `Defaults.kt`, `Memory.kt`, `Kept.kt` |
| The app window | `WebAppActivity.kt`, `WebSheets.kt`, `QuickSheet.kt`, `PagePanels.kt` |
| Blocking | `Filters.kt`, `Blocker.kt`, `Rules.kt` |
| Adding and checking sites | `AddActivity.kt`, `SiteLookup.kt`, `SiteCheck.kt` |
| Insertions | `Addons.kt`, `Runtime.kt`, `Sources.kt`, `Scripts.kt`, `Selectors.kt` |
| Extras | `Powers.kt`, `Net.kt`, `RoutingActivities.kt`, `ControlsActivity.kt` |
| Look | `Ui.kt`, `Glyphs.kt`, `Screen.kt`, `Brand.kt` |
