---
name: jetpack-compose
description: "Use when changing ANONERO's Jetpack Compose UI: screens, navigation graphs, state collection, theming, reusable components, accessibility, or localized strings. Not for wallet, native, or service logic."
---

# Jetpack Compose UI

## When to use

- Adding or changing a Compose screen, component, dialog, bottom sheet, navigation
  route, or theme token.
- Fixing state collection, recomposition, back handling, accessibility, or layout.
- Adding or changing user-visible text and translations.

Do not use for wallet state, Tor/LWS, JNI, or cryptographic changes.

## Read first

- `AGENTS.md`
- `app/AGENTS.md`
- `app/src/main/java/io/anonero/ui/MainActivity.kt`
- `app/src/main/java/io/anonero/ui/NavGraph.kt`
- `app/src/main/java/io/anonero/ui/home/graph/HomeGraph.kt`
- `app/src/main/java/io/anonero/ui/onboard/graph/OnboardingGraph.kt`
- `app/src/main/java/io/anonero/ui/home/graph/routes/HomeRoutes.kt`
- `app/src/main/java/io/anonero/ui/components/`
- `app/src/main/java/io/anonero/ui/theme/`
- `scripts/check-i18n.py`

## Invariants

- `MainActivity` sets `FLAG_SECURE` for the whole window. Do not remove it, bypass it,
  or create a screen that renders wallet data outside that window.
- The lock screen overlay must stay above content until wallet background sync has
  exited. Do not dismiss it optimistically on PIN entry.
- All user-visible text comes from resources. Supported locales are base, `es-rES`,
  `ru-rRU`, and `b+zh+Hans`. Placeholders and plural quantities must match.
- Do not put seeds, passphrases, PINs, wallet passwords, or full sensitive data in
  previews, `contentDescription`, test tags, or error text.
- Keep navigation type-safe: use the existing serializable route classes in
  `ui/home/graph/routes/` and `ui/onboard/graph/`.
- State is hoisted. Composables render state and emit events; wallet and policy logic
  stay in ViewModels/services.
- Do not create a second source of truth with `remember { mutableStateOf(...) }` for
  data already exposed by `WalletState`.
- Use theme tokens and existing components; do not hardcode colors or one-off
  typography unless the design system genuinely lacks a token.
- Accessibility: actionable icons need a meaningful `contentDescription`; decorative
  images may use `null` with a comment; touch targets should meet Material guidance.

## Procedure

1. **Locate the route and graph.** Find the existing route in `HomeRoutes.kt`,
   `HomeGraph.kt`, `NavGraph.kt`, or `OnboardingGraph.kt`. Reuse the route pattern.
2. **Identify the ViewModel.** Use `koinViewModel`/`koinInject` as the surrounding
   screens do. Do not instantiate a repository or wallet directly in a composable.
3. **Collect state correctly.** Use `collectAsState` or `asLiveData().observeAsState`
   consistent with the screen. Keep `remember` only for ephemeral UI state (dialog
   visibility, text field buffer).
4. **Add resources.** Add the base string to `values/strings.xml` or
   `values/strings_i18n.xml`, then all supported locales. Use placeholders instead of
   string concatenation.
5. **Handle lifecycle/back.** Use `BackHandler` where the screen owns a destructive or
   multi-step flow. Do not leave a completed transaction screen one tap away from
   re-sending.
6. **Secure dialogs.** Use `DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)`
   for secret-entry dialogs, as `SecureWipe.kt` does.
7. **Preview safely.** Previews must not include real addresses, balances, seeds, or
   decoy state. Use synthetic values.
8. **Static validation.** Run `python3 scripts/check-i18n.py`; inspect hardcoded text
   and accessibility labels. Do not run Compose or an emulator locally.

## Static checks

```sh
python3 scripts/check-i18n.py
grep -rn 'Text("' app/src/main/java/io/anonero/ui
grep -rn 'contentDescription = "' app/src/main/java/io/anonero/ui
grep -rn 'contentDescription = null' app/src/main/java/io/anonero/ui
grep -rn 'toastState.show("' app/src/main/java/io/anonero/ui
grep -rn "FLAG_SECURE" app/src/main/java
```

Review any `contentDescription = null` to confirm the element is decorative.

## Completion criteria

- [ ] Route/graph wiring is consistent with existing navigation.
- [ ] State comes from the existing owner; no duplicated wallet state.
- [ ] All text and placeholders exist in every supported locale.
- [ ] Accessibility labels are meaningful.
- [ ] `FLAG_SECURE` and lock-screen behavior are intact.
- [ ] Destructive/completed flows cannot be re-triggered by back navigation.
- [ ] No secret or real wallet data in previews.
- [ ] `check-i18n.py` passes; rendering remains unverified until CI/device testing.
