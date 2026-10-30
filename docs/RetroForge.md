# RetroForge Switch Player: notes

The RetroForge Switch Player is Eden (GPL-3.0) with RetroForge branding and defaults. These notes record the changes we make, which emulators we looked at for borrowing fixes, and why.

## Defaults we change

Applied once on first start, to new installs and to installs that update from an older build:

| Setting | Eden default | Ours | Why |
|---|---|---|---|
| Asynchronous shaders | off | on | New scenes don't freeze while shaders build. Some effects may be missing for a moment instead. |
| Disk shader cache | on | on (forced) | Built shaders are kept, so the second visit to a scene is smooth. |
| Resolution | 1× | 0.5× on 4 GB phones, 0.75× on 6 GB, 1× on 8 GB or more | Phones with less memory run out of memory and slow down at 1×. |

A player can still change all three in Settings. They are applied only once, so the player's own choice stays.

## Other Switch emulators, October 2026

In February 2026, Nintendo sent GitHub a DMCA notice naming Eden, Citron, Kenji-NX, Ryubing, MeloNX, Sudachi and other Switch emulators. Eden's GitHub repository was disabled in March 2026, and its source now lives on its own server.

| Emulator | Based on | Licence | State | Use for us |
|---|---|---|---|---|
| Eden | yuzu | GPL-3.0 | Active, best Android compatibility | Our base. |
| Citron (Citron Neo) | yuzu | GPL-3.0 | Revived, active | The only active yuzu-family fork. Its fixes can be ported, with credit, because the licence matches. Check its changes before each Player release. |
| Sudachi, Torzu, Suyu | yuzu | GPL-3.0 | Inactive | Nothing new to port. |
| Kenji-NX | Ryujinx (C#) | MIT | Android builds last updated February 2026; named in the DMCA notice | See below. |
| Ryubing / Ryujinx | Ryujinx (C#) | MIT | Desktop focus; reports of its status conflict | No Android build to borrow from. |

### Kenji-NX

Kenji-NX runs some games that yuzu-family emulators struggle with, because Ryujinx is a separate implementation. It is written in C# on .NET, so none of its code can be dropped into Eden's C++. Its fixes would have to be studied and rewritten. The MIT licence allows that, with credit.

We don't bundle it or add it as a second player for now:

- its Android builds haven't been updated since February 2026;
- it was named in the DMCA notice;
- there is no current compatibility list to show which games it would add.

RetroForge already lets a player pick any emulator installed on the phone, so anyone who wants Kenji-NX can install it themselves.

### Rules

- Only open-source code, used under its licence and credited.
- No decompiling closed-source emulators.
- No leaked Nintendo SDK code.
- No keys, firmware or games are ever shipped. Players copy their own from their Switch.
