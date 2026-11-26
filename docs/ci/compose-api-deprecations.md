# Compose API deprecations

COMP-API-01 covers 57 distinct application source locations in [Tests run 45848781860](https://github.com/daviduhden/ANONERO/actions/runs/45848781860) at `9734264fd465817357847f9aafa73f3390948c33`. The compiler repeats diagnostics across tasks and flavors. The Compose BOM resolves Material 3 1.5.0 and Foundation 1.13.0. The reported families are the headline overload of ListItem, the previous modal sheet state factory, and password visual transformation.

Passphrases cross the IME and Compose editing boundary into existing wallet verification, backup and restore operations. Local observers, clipboard readers and saved-state inspection are relevant threats. Secure text fields must mask every character, retain their editing state only in memory and keep the existing submit and clearing behavior. An incorrect migration could expose the last character, save text across activity recreation or publish a seed after an edit invalidated its verification. A privileged IME may still observe input. No new storage, network recipient or wallet format is required. Revert the UI changes together if manual device checks show an input or verification regression.

Manual validation must compile both flavors, inspect both Kotlin warning streams, exercise list layout and sheet dismiss/expand behavior, and check password masking, IME actions, cancellation, comparison, and recreation with synthetic inputs.

The migration updates 42 list items, six modal sheets and nine secure fields.
The sheet anchors are Hidden and Expanded. The nine fields use ephemeral
TextFieldState instances and explicit Hidden obfuscation. The seed screen
compares the current input with the original submission before displaying
the asynchronous result. The manual anonymity-boundaries job guards the
masking and saved-state choices. Device behavior and removal of these
warnings in compiled output await an authorized run on the changed revision.

Foundation records programmatic clears in its undo history. Passphrase reset
paths also clear that field's undo history. Dismissing seed verification cancels
the outstanding request before clearing the input.
