---
name: iphone-only-apps
description: This user ships iPhone-only apps. Apply this to EVERY iOS/Swift app in this workspace — when scaffolding a new app, adding a target, editing project.pbxproj, uploading a build, or handling App Store Connect screenshots and validation. Trigger it even when the user says nothing about devices, and especially on any mention of iPad, Apple Watch, watchOS, tvOS, universal, device family, or missing/required screenshots.
---

# iPhone-Only Apps (standing user default)

This user builds for iPhone only. This is a persistent preference, not a per-app
question — never ask whether they want iPad or Apple Watch support, and never add it
on your own initiative. Only deviate if the user explicitly asks for another device
in the current request.

Scope: iPhone only. No iPad. No Apple Watch. No tvOS, no Mac Catalyst, no visionOS.

## 1. Set the device family at scaffold time

Every iOS app target must carry this in **all** build configurations (Debug and Release,
for the app target and any test targets) in `project.pbxproj`:

```
TARGETED_DEVICE_FAMILY = "1";
```

`1` = iPhone, `2` = iPad, `"1,2"` = universal. Never leave it at `"1,2"` and never omit
it — an absent key is treated as universal, which is what silently re-introduces the
iPad screenshot requirement.

Also keep these off unless explicitly requested:

- `SUPPORTS_MACCATALYST = NO`
- `SUPPORTS_XR = NO`
- `SUPPORTS_MAC_DESIGNED_FOR_IPHONE_IPAD` — leave unset

Do this in the first coding pass on a new app, not as a later fix. Verify with a grep for
`TARGETED_DEVICE_FAMILY` and confirm every match is `"1"`.

Orientation follows from this: portrait-first iPhone framing. Don't add iPad-specific
layout branches, `NavigationSplitView` for iPad regular width, or size-class variants
aimed at tablets.

## 2. Don't add non-iPhone targets

Never call `swiftAddTarget` with `watch-app` or `tv-app`, and don't suggest them as
"logical next steps." Widgets, share extensions, App Intents and iMessage extensions are
fine — they're iPhone-side — but a watch app is not.

If no watchOS target exists, App Store Connect never asks for Apple Watch screenshots.
That is the mechanism: the absence of the target is the fix, so simply don't create one.

## 3. Why App Store Connect still asks for screenshots

This is the part that bites. ASC derives its required screenshot display sets from the
**device families declared in the uploaded binary**, not from anything you can toggle in
the web UI or set via metadata. There is no "I don't need iPad screenshots" checkbox.

So when ASC demands iPad (or Apple Watch) screenshots:

1. Fix `TARGETED_DEVICE_FAMILY` in `project.pbxproj` (and/or remove the watch target).
2. Run `runChecks` on the app folder.
3. **Upload a NEW build** — a previously uploaded binary keeps its old device families
   forever. Editing the project without shipping a new build changes nothing in ASC.
4. Attach the new build to the version.
5. Re-run validation and confirm the iPad/Watch display sets are gone.

Never try to satisfy the requirement by generating iPad or Watch screenshots. Fix the
binary instead.

iPhone-only does not block iPad users from installing — iOS offers the app in iPhone
compatibility mode. Mention this once if the user seems worried about reach, then drop it.

## 4. Screenshots you do need

Only the iPhone display set. A 6.7"/6.9" iPhone set is sufficient for current ASC
requirements; don't produce 5.5" or iPad sets unless validation specifically asks.

## 5. Verify before claiming done

Before telling the user the app is iPhone-only, confirm both:

- Every `TARGETED_DEVICE_FAMILY` in the project is `"1"`.
- The build currently **attached to the ASC version** was produced after that change.

The second one is the one that gets missed. A stale attached build is why ASC keeps
asking for screenshots the project no longer needs.
